"""Lesson renderers.

`StickmanStudioRenderer` (production) calls our Stickman Studio fork. The fork must expose:

    # module set by STICKMAN_STUDIO_MODULE, default `stickman_studio.cis_pipeline`
    def render_lesson(params: dict, output_dir: str) -> dict:
        '''Render one lesson with the Cinematic Illustrated pipeline (headless, no Qt).
        params keys: topic, script, script_mode="use_as_is", duration, voice_id, format="9:16",
                     cis_visual_style, cis_accent_color, cis_illustration_mode="pil".
        Returns {"video": "<path to 1080x1920 mp4>", "project": "<path to project.json>"}.'''

i.e. a thin wrapper around `CISPipeline(llm_manager=None, tts_manager=..., output_dir, temp_dir).generate_video(params)`
plus the fork patches in Tech Spec §8.8 (NVENC probe, fonts, music mix, SFX, title glyphs).

`PlaceholderRenderer` needs only ffmpeg: a solid-colour 9:16 video of the right length with silent audio and
evenly spaced synthetic caption timings. It lets the full pipeline run locally and in CI without the fork.
"""

from __future__ import annotations

import importlib
import json
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
        self.module = importlib.import_module(get_settings().stickman_studio_module)

    def render(self, params: dict, workdir: Path) -> RenderResult:
        out = self.module.render_lesson(params, str(workdir))
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
