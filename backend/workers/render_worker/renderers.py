"""Lesson renderers.

`FilmRenderer` directs a film around the approved lesson narration and supplies caption word timings.

`StickmanStudioRenderer` runs our Stickman Studio fork in its own virtualenv via
`boomdoom_entry.py` (headless CISPipeline, Kokoro voice). Contract:

    <python> boomdoom_entry.py --params params.json --out <dir>
    -> stdout: {"video": "<1080x1920 mp4>", "project": "<project.json with captions[].words>"}

A subprocess keeps its heavy dependencies out of the backend and lets SIGTERM/timeouts kill a stuck render.

`PlaceholderRenderer` needs only ffmpeg: a solid-colour 9:16 video of the right length with silent audio and
evenly spaced synthetic caption timings, for running the pipeline without the fork.
"""

from __future__ import annotations

import json
import logging
import os
import re
import subprocess
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Protocol

from app.config import get_settings
from workers.script_worker.schema import WORDS_PER_SECOND

log = logging.getLogger(__name__)


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


class FilmRenderer:
    """Default lesson renderer: an OpenAI-directed stickman film (workers/film_worker, stickManCustom.md).

    Speaks the lesson's own narration with Stickman Studio's Kokoro voice, has the model plan and code a 9:16 film
    against vendor/stickman-skill, reviews stills, renders, mixes and audits it. Returns word timings in the same
    shape as Stickman Studio's project.json, so quiz pauses and the manifest work unchanged.
    """

    name = "film"

    def __init__(self) -> None:
        from workers.film_worker.director import Tools
        from workers.film_worker.llm import FilmLLM

        s = get_settings()
        studio = Path(s.stickman_studio_dir)
        skill = Path(s.stickman_skill_dir)
        if not (studio / "boomdoom_tts.py").exists():
            raise RuntimeError(f"STICKMAN_STUDIO_DIR does not contain boomdoom_tts.py: {studio}")
        if not (skill / "scripts" / "build.py").exists():
            raise RuntimeError(f"STICKMAN_SKILL_DIR is not vendor/stickman-skill: {skill}")
        self.tools = Tools(
            skill_dir=skill,
            studio_dir=studio,
            python=s.stickman_python or str(studio / ".venv" / "bin" / "python"),
            models_dir=s.stickman_models_dir,
            voice_id=s.film_voice_id,
            jobs=s.film_render_jobs,
        )
        self.llm_factory = FilmLLM
        self.review_rounds = s.film_review_rounds

    def render(self, params: dict, workdir: Path) -> RenderResult:
        from workers.film_worker.director import make_film

        sentences = split_sentences(params.get("segments") or [{"narration": params["script"]}])
        topic = params.get("topic_name") or params["topic"]
        from workers.common import check_cancelled

        job_id = params.get("job_id")
        result = make_film(
            topic,
            params.get("concept") or params["topic"],
            workdir / "film",
            tools=self.tools,
            llm=self.llm_factory(),
            narration=sentences,
            critique_rounds=self.review_rounds,
            on_stage=lambda stage: check_cancelled(job_id),  # an operator cancel stops it at the next stage
        )
        log.info(
            "film rendered",
            extra={"notes": result.notes[:6], "tokens": result.tokens, "issues": len(result.audit_issues)},
        )
        return RenderResult(result.video, film_project(result))


def split_sentences(segments: list[dict]) -> list[str]:
    """Lesson narration -> spoken sentences, in order, words unchanged (segment timing matches on them)."""
    out: list[str] = []
    for seg in segments:
        out += [x.strip() for x in re.split(r"(?<=[.!?])\s+", seg["narration"].strip()) if x.strip()]
    return out


def film_project(result) -> dict:
    """project.json for a film: per-sentence captions with words spread over each sentence by length."""
    from workers.film_worker.director import picture_times

    captions = []
    for s in picture_times(result.sentences):
        words = s["text"].split()
        total = sum(len(w) + 1 for w in words) or 1
        t, span, timed = s["start"], s["end"] - s["start"], []
        for w in words:
            d = span * (len(w) + 1) / total
            timed.append({"word": w, "start": round(t, 3), "end": round(t + d, 3)})
            t += d
        captions.append({"text": s["text"], "start": s["start"], "end": s["end"], "words": timed})
    return {
        "renderer": "film",
        "duration": result.duration_s,
        "title": result.title,
        "captions": captions,
        "film": {"notes": result.notes, "audit_issues": result.audit_issues, "tokens": result.tokens},
    }


@lru_cache
def get_renderer() -> Renderer:
    """RENDERER=film (default in the POC image), stickman (previous Stickman Studio engine) or placeholder (tests)."""
    kind = get_settings().renderer
    if kind == "film":
        return FilmRenderer()
    if kind == "stickman":
        return StickmanStudioRenderer()
    return PlaceholderRenderer()
