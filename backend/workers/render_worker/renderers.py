"""Lesson renderers.

`StickmanStudioRenderer` (real lessons) runs our Stickman Studio fork in its own virtualenv via
`boomdoom_entry.py` (headless CISPipeline, Kokoro voice). Contract:

    <python> boomdoom_entry.py --params params.json --out <dir>
    -> stdout: {"video": "<1080x1920 mp4>", "project": "<project.json with captions[].words>"}

A subprocess keeps its heavy dependencies out of the backend and lets SIGTERM/timeouts kill a stuck render.

`PlaceholderRenderer` needs only ffmpeg: a solid-colour 9:16 video of the right length with silent audio and
evenly spaced synthetic caption timings, for running the pipeline without the fork.
"""

from __future__ import annotations

import json
import os
import subprocess
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Protocol

from app.config import get_settings
from workers.script_worker.schema import WORDS_PER_SECOND


@dataclass
class RenderResult:
    video: Path
    project: dict


class Renderer(Protocol):
    name: str

    def render(self, params: dict, workdir: Path) -> RenderResult: ...


class StickmanStudioRenderer:
    name = "stickman"

    def __init__(self) -> None:
        s = get_settings()
        self.dir = Path(s.stickman_studio_dir)
        self.python = s.stickman_python or str(self.dir / ".venv" / "bin" / "python")
        self.timeout = s.stickman_timeout_s
        if not (self.dir / "boomdoom_entry.py").exists():
            raise RuntimeError(f"STICKMAN_STUDIO_DIR does not contain boomdoom_entry.py: {self.dir}")

    def render(self, params: dict, workdir: Path) -> RenderResult:
        params_file = workdir / "params.json"
        params_file.write_text(json.dumps(params))
        env = {**os.environ, "PATH": f"{Path(self.python).parent}{os.pathsep}{os.environ.get('PATH', '')}"}
        proc = subprocess.run(
            [self.python, "boomdoom_entry.py", "--params", str(params_file), "--out", str(workdir / "stickman")],
            cwd=self.dir,
            env=env,
            capture_output=True,
            text=True,
            timeout=self.timeout,
        )
        if proc.returncode != 0:
            raise RuntimeError(f"Stickman Studio failed ({proc.returncode}): {proc.stderr[-600:]}")
        out = json.loads(proc.stdout.strip().splitlines()[-1])
        return RenderResult(Path(out["video"]), json.loads(Path(out["project"]).read_text()))


class PlaceholderRenderer:
    name = "placeholder"

    def __init__(self, ffmpeg: str | None = None):
        self.ffmpeg = ffmpeg or get_settings().ffmpeg_bin

    def render(self, params: dict, workdir: Path) -> RenderResult:
        words = params["script"].split()
        duration = max(3.0, len(words) / WORDS_PER_SECOND)
        colour = params.get("cis_accent_color", "#2F6F5E").lstrip("#")
        video = workdir / "render.mp4"
        cmd = [
            self.ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            f"color=c=0x{colour}:s=1080x1920:r=24:d={duration:.2f}",
            "-f",
            "lavfi",
            "-i",
            f"anullsrc=r=44100:cl=mono:d={duration:.2f}",
            "-shortest",
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            str(video),
        ]
        proc = subprocess.run(cmd, capture_output=True, timeout=300)
        if proc.returncode != 0:
            raise RuntimeError(f"placeholder render failed: {proc.stderr.decode(errors='replace')[-300:]}")
        step = duration / max(len(words), 1)
        captions = [
            {
                "words": [
                    {"word": w, "start": round(i * step, 3), "end": round((i + 1) * step, 3)}
                    for i, w in enumerate(words)
                ]
            }
        ]
        return RenderResult(video, {"captions": captions, "duration": duration})


@lru_cache
def get_renderer() -> Renderer:
    return StickmanStudioRenderer() if get_settings().renderer == "stickman" else PlaceholderRenderer()
