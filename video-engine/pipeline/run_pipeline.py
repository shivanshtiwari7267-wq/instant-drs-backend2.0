import argparse
import subprocess
import os
import sys
import time
from pathlib import Path

def get_video_duration(video_path):
    cmd = ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", str(video_path)]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
        return float(result.stdout.strip())
    except Exception as e:
        print(f"Error getting video duration: {e}")
        sys.exit(1)

def format_time(seconds):
    if seconds < 60:
        return f"{seconds:.2f} sec"
    else:
        minutes = int(seconds // 60)
        remaining_seconds = seconds % 60
        return f"{minutes} min {remaining_seconds:.2f} sec"

def get_python_exe(tool_dir):
    """Find the Python executable in the tool's virtual environment, if it exists."""
    for venv_name in ['.venv', 'venv']:
        exe_path = Path(tool_dir) / venv_name / 'Scripts' / 'python.exe'
        if exe_path.exists():
            return str(exe_path)
        exe_path_unix = Path(tool_dir) / venv_name / 'bin' / 'python'
        if exe_path_unix.exists():
            return str(exe_path_unix)
    return sys.executable

def run_command(cmd, description):
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if result.returncode != 0:
            print(f"[Pipeline] Error during {description}:")
            print(result.stderr)
            sys.exit(1)
    except Exception as e:
        print(f"[Pipeline] Unexpected error during {description}: {e}")
        sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="InstantDRS Sequential Video Engine Pipeline")
    parser.add_argument("--sport", required=True, help="Sport name (e.g., volleyball)")
    parser.add_argument("--input", required=True, help="Path to input video")
    parser.add_argument("--output", required=True, help="Path to final review video")
    parser.add_argument("--start", type=float, default=None, help="Start time in seconds")
    parser.add_argument("--duration", type=float, default=None, help="Duration in seconds")
    
    args = parser.parse_args()
    
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    
    print("[Pipeline] Starting...")
    pipeline_start_time = time.perf_counter()
    print(f"[Pipeline] Original input: {input_path}")
    
    if not input_path.exists():
        print(f"Error: Input video does not exist at {input_path}")
        sys.exit(1)
        
    trim_time = 0.0
    current_input_path = input_path
    
    if (args.start is not None and args.duration is None) or (args.start is None and args.duration is not None):
        print("Error: Both --start and --duration must be provided together.")
        sys.exit(1)
        
    if args.start is not None and args.duration is not None:
        if args.start < 0:
            print("Error: --start must be >= 0")
            sys.exit(1)
        if args.duration <= 0:
            print("Error: --duration must be > 0")
            sys.exit(1)
            
        vid_duration = get_video_duration(input_path)
        if args.start >= vid_duration:
            print(f"Error: --start ({args.start}) must be within the video duration ({vid_duration})")
            sys.exit(1)
        if args.start + args.duration > vid_duration:
            print(f"Error: --start + --duration ({args.start + args.duration}) exceeds video duration ({vid_duration})")
            sys.exit(1)
            
    supported_sports = ['cricket', 'volleyball', 'basketball', 'football', 'tennis']
    if args.sport.lower() not in supported_sports:
        print(f"Error: Unsupported sport '{args.sport}'. Supported sports: {', '.join(supported_sports)}")
        sys.exit(1)
        
    pipeline_dir = Path(__file__).resolve().parent
    base_dir = pipeline_dir.parent
    
    working_dir = pipeline_dir / 'working'
    working_dir.mkdir(exist_ok=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    if args.start is not None and args.duration is not None:
        print("\n[Pipeline] Running Input Trimming...")
        drs_window_path = working_dir / 'drs_window.mp4'
        cmd_trim = [
            "ffmpeg", "-y",
            "-ss", str(args.start),
            "-i", str(input_path),
            "-t", str(args.duration),
            "-c:v", "libx264",
            "-an",
            str(drs_window_path)
        ]
        trim_start_time = time.perf_counter()
        run_command(cmd_trim, "Input Trimming")
        trim_time = time.perf_counter() - trim_start_time
        current_input_path = drs_window_path
        print(f"[Pipeline] Trimming completed. Saved to {current_input_path}")
    
    # Tool 1: Slow Motion
    print("\n[Pipeline] Running Tool 1...")
    step1_out = working_dir / 'step1_slow_motion.mp4'
    print(f"[Pipeline] Tool 1 input: {current_input_path}")
    print(f"[Pipeline] Tool 1 output: {step1_out}")
    
    tool1_dir = base_dir / 'tool1-slow-motion'
    cmd1 = [
        get_python_exe(tool1_dir),
        str(tool1_dir / 'slow_motion.py'),
        str(current_input_path),
        str(step1_out),
        "0.5" # Half speed
    ]
    tool1_start_time = time.perf_counter()
    run_command(cmd1, "Running Tool 1")
    tool1_time = time.perf_counter() - tool1_start_time
    print("[Pipeline] Tool 1 completed.")
    
    # Tool 2: RIFE High-FPS/Interpolated
    print("\n[Pipeline] Running Tool 2...")
    step2_out = working_dir / 'step2_interpolated.mp4'
    print(f"[Pipeline] Tool 2 input: {step1_out}")
    print(f"[Pipeline] Tool 2 output: {step2_out}")
    
    tool2_dir = base_dir / 'tool2-120fps'
    rife_dir = base_dir / 'rife'
    cmd2 = [
        get_python_exe(tool2_dir),
        str(rife_dir / 'inference_video.py'),
        "--video", str(step1_out),
        "--output", str(step2_out),
        "--exp", "2",
        "--scale", "0.5",
        "--model", str(rife_dir / 'train_log')
    ]
    tool2_start_time = time.perf_counter()
    run_command(cmd2, "Running Tool 2")
    tool2_time = time.perf_counter() - tool2_start_time
    print("[Pipeline] Tool 2 completed.")
    
    # Tool 3: Sports Ball Tracking
    step3_out = working_dir / 'step3_tracking.mp4'
    print("\n[Pipeline] Running Tool 3...")
    print(f"[Pipeline] Tool 3 input: {step2_out}")
    print(f"[Pipeline] Tool 3 output: {step3_out}")
    
    # Extract frames (Required by Tool 3 API) - REMOVED in Phase 2
    extraction_time = 0.0
    
    tool3_dir = base_dir / 'tool3-sports-ball-tracking'
    cmd3 = [
        get_python_exe(tool3_dir),
        str(tool3_dir / 'main.py'),
        "--sport", args.sport,
        "--video", str(step2_out),
        "--output", str(step3_out)
    ]
    tool3_start_time = time.perf_counter()
    run_command(cmd3, "Running Tool 3")
    tool3_time = time.perf_counter() - tool3_start_time
    print("[Pipeline] Tool 3 completed.")
    
    # Stage 4: FFmpeg Audio Mux (Stream Copy)
    print("\n[Pipeline] Running Stage 4 (Audio Mux)...")
    print(f"[Pipeline] Video input: {step3_out}")
    print(f"[Pipeline] Audio input: {step1_out}")
    print(f"[Pipeline] Final output: {output_path}")
    
    cmd4 = [
        "ffmpeg", "-y",
        "-i", str(step3_out),
        "-i", str(step1_out),
        "-c:v", "copy",
        "-c:a", "copy",
        "-map", "0:v:0",
        "-map", "1:a:0?",
        str(output_path)
    ]
    mux_start_time = time.perf_counter()
    run_command(cmd4, "Running Stage 4 Audio Mux")
    mux_time = time.perf_counter() - mux_start_time
    print("[Pipeline] Stage 4 Audio Mux completed.")
    
    total_pipeline_time = time.perf_counter() - pipeline_start_time
    measured_stages = trim_time + tool1_time + tool2_time + extraction_time + tool3_time + mux_time
    pipeline_overhead = total_pipeline_time - measured_stages

    print("\n[Benchmark]")
    print(f"Tool 1 - Slow Motion: {tool1_time:.2f} seconds")
    print(f"Tool 2 - RIFE 120 FPS: {tool2_time:.2f} seconds")
    print(f"Tool 3 - Ball Tracking: {tool3_time:.2f} seconds")
    print(f"Stage 4 - Audio Mux: {mux_time:.2f} seconds")
    print(f"Total Pipeline Time: {total_pipeline_time:.2f} seconds\n")

    print("\n[Pipeline] Sequential processing completed successfully.")
    print(f"Final output:\n{output_path.relative_to(Path.cwd()) if output_path.is_relative_to(Path.cwd()) else output_path}")

if __name__ == '__main__':
    main()
