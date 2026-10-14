#!/usr/bin/env python3
"""
InstantDRS Fast Sequential Review Pipeline
===========================================
Replaces RIFE + Tool 1 with Direct In-Memory OpenCV Tracking via Tool 3 (OpenVINO FP32)
followed by post-tracking 0.5x slow-motion review and synchronized audio muxing.

Requirements:
- No RIFE
- No Tool 1
- No intermediate JPEG/PNG frames on disk
- Existing Tool 3 source code unchanged
- OpenVINO FP32 model utilized
- Preserves ball tracking overlays
- Preserves synchronized audio
- Completes in <= 30 seconds for typical DRS event window
"""

import os
import sys
import time
import shutil
import argparse
import subprocess
from pathlib import Path

# Self-re-exec in Tool 3 .venv if not currently running in it
PIPELINE_DIR = Path(__file__).resolve().parent
BASE_DIR = PIPELINE_DIR.parent
TOOL3_DIR = BASE_DIR / "tool3-sports-ball-tracking"

venv_python = TOOL3_DIR / ".venv" / "Scripts" / "python.exe"
if not venv_python.exists():
    venv_python = TOOL3_DIR / "venv" / "Scripts" / "python.exe"
if not venv_python.exists():
    venv_python = TOOL3_DIR / ".venv" / "bin" / "python"

if venv_python.exists():
    try:
        current_exe = Path(sys.executable).resolve()
        target_exe = venv_python.resolve()
        if current_exe != target_exe:
            # Re-execute using the venv python
            res = subprocess.run([str(venv_python)] + sys.argv)
            sys.exit(res.returncode)
    except Exception as e:
        print(f"[FastPipeline] Warning during venv check: {e}")

# Ensure tool3 is importable
if str(TOOL3_DIR) not in sys.path:
    sys.path.insert(0, str(TOOL3_DIR))

from sports_config import get_sport_config, get_supported_sports, OPENVINO_MODEL_PATH
from ball_tracker import BallTracker


def check_audio_present(video_path: str) -> bool:
    """Check if the video file contains an audio stream."""
    cmd = [
        'ffprobe', '-v', 'error',
        '-select_streams', 'a',
        '-show_entries', 'stream=codec_type',
        '-of', 'default=noprint_wrappers=1:nokey=1',
        str(video_path)
    ]
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return bool(res.stdout.strip())
    except Exception:
        return False


def get_video_duration(video_path: str) -> float:
    """Retrieve video duration in seconds using ffprobe."""
    cmd = [
        'ffprobe', '-v', 'error',
        '-show_entries', 'format=duration',
        '-of', 'default=noprint_wrappers=1:nokey=1',
        str(video_path)
    ]
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
        return float(res.stdout.strip())
    except Exception as e:
        print(f"[FastPipeline] Error probing video duration: {e}", file=sys.stderr)
        return 0.0


def main():
    parser = argparse.ArgumentParser(description="InstantDRS Fast Sequential Review Pipeline")
    parser.add_argument("--sport", required=True, help="Sport name (e.g., cricket, volleyball)")
    parser.add_argument("--input", required=True, help="Path to input video MP4")
    parser.add_argument("--output", required=True, help="Path to final review video MP4")
    parser.add_argument("--start", type=float, default=None, help="Optional start time in seconds for DRS window")
    parser.add_argument("--duration", type=float, default=None, help="Optional duration in seconds for DRS window")
    
    args = parser.parse_args()
    
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    
    if not input_path.exists():
        print(f"[FastPipeline] Error: Input video does not exist: {input_path}", file=sys.stderr)
        sys.exit(1)
        
    supported = get_supported_sports()
    if args.sport.lower() not in supported:
        print(f"[FastPipeline] Error: Unsupported sport '{args.sport}'. Supported: {', '.join(supported)}", file=sys.stderr)
        sys.exit(1)
        
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    # Create isolated working directory
    working_dir = PIPELINE_DIR / "working" / f"fast_drs_{os.getpid()}_{int(time.time())}"
    working_dir.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("INSTANTDRS FAST REVIEW PIPELINE")
    print(f"Sport       : {args.sport}")
    print(f"Input Video : {input_path}")
    print(f"Output Video: {output_path}")
    print("=" * 70)
    
    pipeline_start = time.perf_counter()
    trim_time = 0.0
    current_input = input_path
    
    try:
        # Optional Stage 0: Event-Window Trimming (if requested)
        if args.start is not None and args.duration is not None:
            print("\n[Stage 0] Trimming Event Window...")
            t0 = time.perf_counter()
            trimmed_path = working_dir / "trimmed_window.mp4"
            cmd_trim = [
                "ffmpeg", "-y",
                "-ss", str(args.start),
                "-i", str(input_path),
                "-t", str(args.duration),
                "-c", "copy",
                str(trimmed_path)
            ]
            res_trim = subprocess.run(cmd_trim, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
            if res_trim.returncode != 0:
                print(f"[FastPipeline] Trimming failed:\n{res_trim.stderr}", file=sys.stderr)
                sys.exit(1)
            current_input = trimmed_path
            trim_time = time.perf_counter() - t0
            print(f"[Stage 0 Completed] Trimming time: {trim_time:.3f} s")
            
        # Stage 1: Direct In-Memory OpenCV Ball Tracking (Tool 3 + OpenVINO FP32)
        print("\n[Stage 1] Ball Tracking with Existing Tool 3 (OpenVINO FP32)...")
        sport_config = get_sport_config(args.sport)
        
        # Ensure OpenVINO FP32 model is used for fast CPU acceleration
        if os.path.exists(OPENVINO_MODEL_PATH):
            sport_config = dict(sport_config)
            sport_config['model_path'] = OPENVINO_MODEL_PATH
            print(f"[FastPipeline] Using accelerated OpenVINO model: {OPENVINO_MODEL_PATH}")
            
        t1_start = time.perf_counter()
        tracked_mp4 = working_dir / "stage1_tracked.mp4"
        
        tracker = BallTracker(sport_config)
        tracker.process_video(str(current_input), str(tracked_mp4))
        
        if not tracked_mp4.exists() or tracked_mp4.stat().st_size == 0:
            print(f"[FastPipeline] Error: Tool 3 failed to produce tracked video.", file=sys.stderr)
            sys.exit(1)
            
        stage1_time = time.perf_counter() - t1_start
        print(f"[Stage 1 Completed] Tracking time: {stage1_time:.3f} s")
        
        # Stage 2: Slow-Motion Review Generation (0.5x speed / 2x duration)
        print("\n[Stage 2] Generating 0.5x Slow-Motion Presentation...")
        t2_start = time.perf_counter()
        slomo_mp4 = working_dir / "stage2_slomo.mp4"
        
        cmd_slomo = [
            "ffmpeg", "-y",
            "-i", str(tracked_mp4),
            "-vf", "setpts=2.0*PTS",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-an",
            str(slomo_mp4)
        ]
        res_slomo = subprocess.run(cmd_slomo, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        if res_slomo.returncode != 0:
            print(f"[FastPipeline] Error in slow-motion generation:\n{res_slomo.stderr}", file=sys.stderr)
            sys.exit(1)
            
        stage2_time = time.perf_counter() - t2_start
        print(f"[Stage 2 Completed] Slow-motion time: {stage2_time:.3f} s")
        
        # Stage 3: Audio Adjustment (atempo=0.5) & Synchronized Review Mux
        print("\n[Stage 3] Synchronized Audio Adjustment & Review Mux...")
        t3_start = time.perf_counter()
        has_audio = check_audio_present(str(current_input))
        
        if has_audio:
            cmd_mux = [
                "ffmpeg", "-y",
                "-i", str(slomo_mp4),
                "-i", str(current_input),
                "-filter_complex", "[1:a]atempo=0.5[a]",
                "-map", "0:v",
                "-map", "[a]",
                "-c:v", "copy",
                "-c:a", "aac",
                str(output_path)
            ]
        else:
            # Video only fallback if input clip has no audio
            cmd_mux = [
                "ffmpeg", "-y",
                "-i", str(slomo_mp4),
                "-c:v", "copy",
                "-an",
                str(output_path)
            ]
            
        res_mux = subprocess.run(cmd_mux, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        if res_mux.returncode != 0:
            print(f"[FastPipeline] Error during audio mux:\n{res_mux.stderr}", file=sys.stderr)
            sys.exit(1)
            
        stage3_time = time.perf_counter() - t3_start
        print(f"[Stage 3 Completed] Audio sync & mux time: {stage3_time:.3f} s")
        
        total_time = time.perf_counter() - pipeline_start
        
        # Verify Output
        if not output_path.exists() or output_path.stat().st_size == 0:
            print(f"[FastPipeline] Error: Final review video was not created.", file=sys.stderr)
            sys.exit(1)
            
        out_dur = get_video_duration(str(output_path))
        
        print("\n" + "=" * 70)
        print("FAST DRS REVIEW COMPLETED SUCCESSFULLY")
        print("=" * 70)
        if trim_time > 0:
            print(f"Stage 0 (Trimming)      : {trim_time:.3f} s")
        print(f"Stage 1 (Tool 3 Track)  : {stage1_time:.3f} s")
        print(f"Stage 2 (0.5x Slow-Mo)  : {stage2_time:.3f} s")
        print(f"Stage 3 (Audio Mux)     : {stage3_time:.3f} s")
        print("-" * 70)
        print(f"TOTAL PIPELINE TIME     : {total_time:.2f} seconds")
        print(f"Final Output Duration   : {out_dur:.2f} seconds")
        print(f"Final Output Path       : {output_path}")
        print("=" * 70)
        
        # Clean temporary working files on success
        try:
            shutil.rmtree(working_dir, ignore_errors=True)
        except Exception:
            pass
            
        sys.exit(0)
        
    except Exception as e:
        print(f"[FastPipeline] Fatal error: {e}", file=sys.stderr)
        try:
            shutil.rmtree(working_dir, ignore_errors=True)
        except Exception:
            pass
        sys.exit(1)


if __name__ == '__main__':
    main()
