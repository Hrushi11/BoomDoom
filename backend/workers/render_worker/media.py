"""FFmpeg steps for the render worker (Tech Spec §8.8)."""

from __future__ import annotations

import subprocess
from pathlib import Path

from app.config import get_settings

TIMEOUT_S = 600


class Media:
    def __init__(self, ffmpeg: str | None = None):
        self.ffmpeg = ffmpeg or get_settings().ffmpeg_bin

    def _run(self, args: list[str]) -> None:
        proc = subprocess.run(
            [self.ffmpeg, "-hide_banner", "-loglevel", "error", "-y", *args], capture_output=True, timeout=TIMEOUT_S
        )
        if proc.returncode != 0:
            raise RuntimeError(f"ffmpeg failed: {proc.stderr.decode(errors='replace')[-500:]}")

    def compress(self, src: Path, dst: Path) -> None:
        """1080x1920 -> 720x1280 H.264/AAC, faststart for streaming."""
        self._run(
            [
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
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                "-movflags",
                "+faststart",
                str(dst),
            ]
        )

    def thumbnail(self, src: Path, dst: Path, duration_s: float) -> None:
        """One 360-px-wide JPEG at 25% of the duration."""
        self._run(
            [
                "-ss",
                f"{duration_s * 0.25:.2f}",
                "-i",
                str(src),
                "-frames:v",
                "1",
                "-vf",
                "scale=360:-2",
                "-q:v",
                "4",
                str(dst),
            ]
        )

    def has_working_nvenc(self) -> bool:
        """Fork patch (§8.8): probe with a 1-frame encode instead of trusting `ffmpeg -encoders`."""
        try:
            self._run(
                [
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
            )
            return True
        except (RuntimeError, OSError, subprocess.TimeoutExpired):
            return False
