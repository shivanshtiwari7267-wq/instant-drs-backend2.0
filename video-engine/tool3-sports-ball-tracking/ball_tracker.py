import cv2
import os
import json
import math
import numpy as np
from ultralytics import YOLO

class BallTracker:
    def __init__(self, sport_config):
        """
        Initializes the ball tracker with a specific sport configuration.
        """
        self.sport_name = sport_config['display_name']
        self.model_path = sport_config['model_path']
        self.ball_class_id = sport_config['ball_class_id']
        self.conf_threshold = sport_config['confidence_threshold']
        
        print(f"Loading model for {self.sport_name} from {self.model_path}...")
        self.model = YOLO(self.model_path)
        
        self.reset_tracker()
        
    def reset_tracker(self):
        """Resets the tracking state."""
        self.state = "LOST" # States: LOST, DETECTED, TRACKED
        self.x = 0.0
        self.y = 0.0
        self.radius = 0.0
        self.missed_frames = 0
        self.max_missed_frames = 20 # Allow some frames of tracking
        
        # Kalman Filter setup (x, y, dx, dy)
        self.kf = cv2.KalmanFilter(4, 2)
        self.kf.measurementMatrix = np.array([[1, 0, 0, 0],
                                              [0, 1, 0, 0]], np.float32)
        self.kf.transitionMatrix = np.array([[1, 0, 1, 0],
                                             [0, 1, 0, 1],
                                             [0, 0, 1, 0],
                                             [0, 0, 0, 1]], np.float32)
        self.kf.processNoiseCov = np.array([[1, 0, 0, 0],
                                            [0, 1, 0, 0],
                                            [0, 0, 1, 0],
                                            [0, 0, 0, 1]], np.float32) * 0.03
                                            
        # Optical flow state
        self.prev_gray = None
        self.prev_pts = None
        
        self.current_tracked_sequence = 0
        
        self.stats = {
            'total_input_frames': 0,
            'processed_frames': 0,
            'detected_frames': 0,
            'tracked_frames': 0,
            'lost_frames': 0,
            'reacquisitions': 0,
            'longest_detection_gap': 0,
            'longest_tracked_gap': 0,
            'current_gap': 0,
            'of_success': 0,
            'of_rejected': 0,
            'disagreements': 0,
            'ever_detected': False
        }
        
    def init_kalman(self, x, y):
        """Initialize the Kalman filter state."""
        self.kf.statePre = np.array([[x], [y], [0], [0]], dtype=np.float32)
        self.kf.statePost = np.array([[x], [y], [0], [0]], dtype=np.float32)

    def validate_metadata(self, metadata, frames_dir):
        """Validates the metadata format and logic."""
        required_keys = ["fps", "frame_width", "frame_height", "total_frames", "frames"]
        for key in required_keys:
            if key not in metadata:
                raise ValueError(f"Metadata is missing required key: {key}")
                
        if metadata["total_frames"] <= 0:
            raise ValueError("total_frames must be greater than zero")
            
        if len(metadata["frames"]) != metadata["total_frames"]:
            raise ValueError(f"Metadata frames count mismatch.")
            
        prev_frame_num = 0
        for frame_data in metadata["frames"]:
            frame_num = frame_data["frame_number"]
            if frame_num != prev_frame_num + 1:
                raise ValueError(f"Frame numbers are not sequential.")
            prev_frame_num = frame_num
            frame_file = os.path.join(frames_dir, f"frame_{frame_num:06d}.jpg")
            if not os.path.exists(frame_file):
                raise FileNotFoundError(f"Expected frame file not found: {frame_file}")

    def detect_ball_candidates(self, frame):
        """Runs YOLO and returns a list of candidate balls. Uses larger imgsz to find small balls."""
        # imgsz=1280 helps find much smaller objects at the cost of processing speed
        results = self.model.predict(frame, classes=[self.ball_class_id], conf=self.conf_threshold, imgsz=1280, verbose=False)
        candidates = []
        if len(results) > 0 and results[0].boxes is not None:
            for box in results[0].boxes:
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                conf = float(box.conf[0].cpu().numpy())
                cx = (x1 + x2) / 2.0
                cy = (y1 + y2) / 2.0
                r = max(x2 - x1, y2 - y1) / 2.0 + 2
                candidates.append({'x': cx, 'y': cy, 'r': r, 'conf': conf})
        return candidates

    def estimate_optical_flow(self, current_gray):
        """Uses Lucas-Kanade optical flow to estimate movement from previous frame."""
        of_x, of_y = None, None
        if self.prev_gray is not None and self.prev_pts is not None and self.state in ["DETECTED", "TRACKED"]:
            p1, st, err = cv2.calcOpticalFlowPyrLK(self.prev_gray, current_gray, self.prev_pts, None, 
                                                   winSize=(15, 15), maxLevel=2, 
                                                   criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 0.03))
            
            if p1 is not None and len(p1) > 0:
                good_new = p1[st==1]
                good_old = self.prev_pts[st==1]
                
                if len(good_new) > 0:
                    dx = np.median(good_new[:, 0] - good_old[:, 0])
                    dy = np.median(good_new[:, 1] - good_old[:, 1])
                    
                    of_x = self.x + dx
                    of_y = self.y + dy
                    
                    # Update points for next frame tracking
                    self.prev_pts = good_new.reshape(-1, 1, 2)
        return of_x, of_y

    def update_optical_flow_features(self, gray_frame, x, y, r):
        """Extracts features around the ball to track in the next frame.
        
        Phase 3A optimisation: crops the grayscale frame to the ball's bounding-box
        ROI before calling goodFeaturesToTrack(). OpenCV computes the Shi-Tomasi
        eigenvalue response over the full image even when a mask is provided, so
        reducing the image from 848×478 to a small crop (~2*roi_r square) avoids
        processing ~99% of the pixels and yields a large speed-up with no change to
        tracking logic or parameters.
        """
        h, w = gray_frame.shape[:2]
        roi_r = int(r * 2.5)  # Look for features on the ball and immediate edges

        # --- Phase 3A: compute gFTT on a cropped ROI instead of the full frame ---
        cx, cy = int(x), int(y)
        x1 = max(0, cx - roi_r)
        y1 = max(0, cy - roi_r)
        x2 = min(w, cx + roi_r + 1)
        y2 = min(h, cy + roi_r + 1)

        roi_valid = (x2 > x1) and (y2 > y1) and (x1 < w) and (y1 < h)

        if roi_valid:
            # Run gFTT on the small crop; all parameters unchanged
            roi_gray = gray_frame[y1:y2, x1:x2]
            p0 = cv2.goodFeaturesToTrack(roi_gray, maxCorners=10, qualityLevel=0.1, minDistance=2)
            if p0 is not None:
                # Remap feature coordinates from crop-space back to full-frame space
                p0[:, 0, 0] += x1
                p0[:, 0, 1] += y1
                self.prev_pts = p0
            else:
                self.prev_pts = np.array([[[np.float32(x), np.float32(y)]]])
        else:
            # Fallback: original masked full-frame behaviour (ROI is out of bounds)
            mask = np.zeros_like(gray_frame)
            cv2.circle(mask, (cx, cy), roi_r, 255, -1)
            p0 = cv2.goodFeaturesToTrack(gray_frame, mask=mask, maxCorners=10, qualityLevel=0.1, minDistance=2)
            if p0 is not None:
                self.prev_pts = p0
            else:
                self.prev_pts = np.array([[[np.float32(x), np.float32(y)]]])

    def associate_candidate(self, candidates, target_x, target_y):
        """Associates detected candidates with the predicted trajectory."""
        if not candidates:
            return None
            
        if self.state == "LOST" or target_x is None:
            return max(candidates, key=lambda c: c['conf'])
            
        best_candidate = None
        min_dist = float('inf')
        
        # Max search radius
        max_dist_threshold = max(self.radius * 10, 150)
        
        for c in candidates:
            dist = math.hypot(c['x'] - target_x, c['y'] - target_y)
            size_ratio = c['r'] / max(self.radius, 1.0)
            
            if dist < min_dist and dist < max_dist_threshold and 0.2 < size_ratio < 5.0:
                min_dist = dist
                best_candidate = c
                
        return best_candidate

    def update_state(self, new_state, x, y):
        """Manages state transitions and statistics."""
        old_state = self.state
        self.state = new_state
        self.x = x
        self.y = y
        
        if new_state == "DETECTED":
            if not self.stats['ever_detected']:
                self.stats['ever_detected'] = True
                self.init_kalman(x, y)
            elif old_state == "LOST":
                self.stats['reacquisitions'] += 1
                self.init_kalman(x, y)
                
            self.missed_frames = 0
            self.stats['detected_frames'] += 1
            
            if self.stats['current_gap'] > self.stats['longest_detection_gap']:
                self.stats['longest_detection_gap'] = self.stats['current_gap']
            self.stats['current_gap'] = 0
            
            if self.current_tracked_sequence > self.stats['longest_tracked_gap']:
                self.stats['longest_tracked_gap'] = self.current_tracked_sequence
            self.current_tracked_sequence = 0
            
        elif new_state == "TRACKED":
            self.missed_frames += 1
            self.stats['tracked_frames'] += 1
            self.stats['current_gap'] += 1
            self.current_tracked_sequence += 1
            
            if self.missed_frames > self.max_missed_frames:
                self.state = "LOST"
                
        elif new_state == "LOST":
            self.stats['lost_frames'] += 1
            self.stats['current_gap'] += 1
            
            if self.current_tracked_sequence > self.stats['longest_tracked_gap']:
                self.stats['longest_tracked_gap'] = self.current_tracked_sequence
            self.current_tracked_sequence = 0

    def draw_ball_highlight(self, frame):
        """Draws the green/orange highlight based on state."""
        if self.state == "DETECTED":
            color = (0, 255, 0)
            label = "DETECTED"
        elif self.state == "TRACKED":
            color = (0, 165, 255)
            label = "TRACKED"
        else:
            return
            
        cx, cy = int(self.x), int(self.y)
        r = int(self.radius)
        cv2.circle(frame, (cx, cy), r, color, 3)
        cv2.circle(frame, (cx, cy), 2, color, -1)
        cv2.putText(frame, label, (cx - 20, cy - r - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)

    def print_final_report(self, output_fps, output_width, output_height):
        if self.stats['current_gap'] > self.stats['longest_detection_gap']:
            self.stats['longest_detection_gap'] = self.stats['current_gap']
        if self.current_tracked_sequence > self.stats['longest_tracked_gap']:
            self.stats['longest_tracked_gap'] = self.current_tracked_sequence
            
        print("\n=== HYBRID TRACKING REPORT ===")
        print(f"Total Input Frames: {self.stats['total_input_frames']}")
        print(f"Frames Processed:   {self.stats['processed_frames']}")
        print("-" * 30)
        print(f"Direct Detections (YOLO):  {self.stats['detected_frames']}")
        print(f"Tracked/Predicted Frames:  {self.stats['tracked_frames']}")
        print(f"Frames without Ball:       {self.stats['lost_frames']}")
        print("-" * 30)
        print(f"Total Track Reacquisitions: {self.stats['reacquisitions']}")
        print(f"Longest Detection Gap:      {self.stats['longest_detection_gap']} frames")
        print(f"Longest Tracked Gap:        {self.stats['longest_tracked_gap']} frames")
        print("-" * 30)
        print(f"Optical Flow Successes:     {self.stats['of_success']}")
        print(f"Optical Flow Rejections:    {self.stats['of_rejected']}")
        print("-" * 30)
        print(f"Output Video: {output_width}x{output_height} @ {output_fps:.2f} FPS")
        print("==============================\n")

    def process_video(self, video_path: str, output_path: str):
        if not os.path.exists(video_path):
            raise FileNotFoundError(f"Input video missing: {video_path}")
            
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise RuntimeError(f"Could not open video {video_path}")
            
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = float(cap.get(cv2.CAP_PROP_FPS))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        self.reset_tracker()
        self.stats['total_input_frames'] = total_frames
        
        print(f"Video Info: {width}x{height} at {fps:.2f} FPS, {total_frames} frames total.")
        
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        out = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
        
        if not out.isOpened():
            raise RuntimeError(f"Could not open VideoWriter")

        try:
            frame_num = 0
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                    
                frame_num += 1
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                self.stats['processed_frames'] += 1
                
                if frame_num % 30 == 0:
                    print(f"Processed {frame_num}/{total_frames} frames...")
                
                # 1. Optical Flow Estimation
                of_x, of_y = self.estimate_optical_flow(gray)
                
                # 2. Kalman Prediction
                pred = self.kf.predict()
                kf_x, kf_y = float(pred[0]), float(pred[1])
                
                # 3. YOLO Detection (Run every 3 frames)
                if frame_num % 3 == 0:
                    candidates = self.detect_ball_candidates(frame)
                else:
                    candidates = []
                
                # 4. Association Target (Prefer OF, fallback to KF)
                target_x = of_x if of_x is not None else (kf_x if self.state != "LOST" else None)
                target_y = of_y if of_y is not None else (kf_y if self.state != "LOST" else None)
                
                best_candidate = self.associate_candidate(candidates, target_x, target_y)
                
                # 5. Hybrid Update
                if best_candidate is not None:
                    final_x, final_y = best_candidate['x'], best_candidate['y']
                    self.radius = best_candidate['r']
                    
                    self.kf.correct(np.array([[final_x], [final_y]], dtype=np.float32))
                    self.update_state("DETECTED", final_x, final_y)
                    self.update_optical_flow_features(gray, final_x, final_y, self.radius)
                    
                    if of_x is not None:
                        self.stats['of_success'] += 1
                else:
                    if of_x is not None:
                        dist_of_kf = math.hypot(of_x - kf_x, of_y - kf_y)
                        if dist_of_kf > max(self.radius * 5, 50):
                            self.stats['of_rejected'] += 1
                            final_x, final_y = kf_x, kf_y
                        else:
                            self.stats['of_success'] += 1
                            final_x, final_y = of_x, of_y
                            self.kf.correct(np.array([[final_x], [final_y]], dtype=np.float32))
                            
                        self.update_state("TRACKED", final_x, final_y)
                        
                        if self.missed_frames < 5:
                            self.update_optical_flow_features(gray, final_x, final_y, self.radius)
                    elif self.state in ["DETECTED", "TRACKED"]:
                        final_x, final_y = kf_x, kf_y
                        self.update_state("TRACKED", final_x, final_y)
                    else:
                        self.update_state("LOST", 0, 0)
                        
                self.prev_gray = gray
                
                self.draw_ball_highlight(frame)
                out.write(frame)
                
        finally:
            cap.release()
            out.release()
            print("Video processing complete.")
            self.print_final_report(fps, width, height)

