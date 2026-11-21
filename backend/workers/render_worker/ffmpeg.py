"""FFmpeg commands for compression and thumbnails (Tech Spec §8.8)."""

import subprocess
from pathlib import Path


def compress_cmd(src: Path, dst: Path) -> list[str]:
    return [
        "ffmpeg",
        "-y",
        "-i",
        str(src),
        "-vf",
        "scale=720:1280",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "26",
        "-c:a",
        "aac",
        "-b:a",
        "96k",
        "-movflags",
        "+faststart",
        str(dst),
    ]


def thumbnail_cmd(src: Path, dst: Path, duration_s: float) -> list[str]:
    return [
        "ffmpeg",
        "-y",
        "-ss",
        f"{duration_s * 0.25:.2f}",
        "-i",
        str(src),
        "-frames:v",
        "1",
        "-vf",
        "scale=360:-2",
        str(dst),
    ]


def has_working_nvenc() -> bool:
    """Fork patch: probe with a 1-frame encode instead of trusting `ffmpeg -encoders`."""
    probe = [
        "ffmpeg",
        "-hide_banner",
        "-f",
        "lavfi",
        "-i",
        "color=c=black:s=64x64:d=0.04",
        "-frames:v",
        "1",
        "-c:v",
        "h264_nvenc",
        "-f",
        "null",
        "-",
    ]
    try:
        return subprocess.run(probe, capture_output=True, timeout=15).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True, capture_output=True)
