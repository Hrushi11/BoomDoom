"""Fakes for OpenAI, the renderer and ffmpeg, plus a helper to run workers until queues are empty."""

from __future__ import annotations

import shutil
from pathlib import Path

from sqlalchemy import text

from app.db import get_engine
from workers.common import Route, Worker
from workers.render_worker import pipeline as render_pipeline
from workers.render_worker.renderers import RenderResult
from workers.script_worker import pipeline as script_pipeline
from workers.script_worker.llm import Usage


def make_script(length_s: int = 45, fmt: str = "explainer") -> dict:
    per_seg = length_s * 3 // 3

    def seg(i):
        return {
            "id": f"s{i}",
            "narration": " ".join([f"w{i}"] * per_seg),
            "scene": "stickman",
            "speaker": "narrator",
        }

    return {
        "concept": "c",
        "format": fmt,
        "level": "beginner",
        "language": "en",
        "segments": [seg(1), seg(2), seg(3)],
        "interactions": [
            {
                "id": "i1",
                "after_segment": "s1",
                "type": "predict",
                "question": "Why?",
                "options": ["a", "b", "c"],
                "answer_index": 1,
                "feedback_correct": "y",
                "feedback_wrong": "n",
            }
        ],
        "recap": "recap",
        "go_deeper": ["more"],
        "disclaimer": None,
    }


class FakeLLM:
    def __init__(self, qa_pass: bool = True, allow: bool = True, fail_first: int = 0):
        self.qa_pass, self.allow, self.fail_first = qa_pass, allow, fail_first
        self.calls: list[str] = []

    def json(self, *, model, prompt, schema, temperature):
        from workers.common import RetryableError

        self.calls.append(schema["name"])
        if self.fail_first:
            self.fail_first -= 1
            raise RetryableError("openai unavailable")
        u = Usage(model, 1000, 500)
        name = schema["name"]
        if name == "syllabus":
            return {"concepts": [{"title": f"Concept {i}", "objective": f"Learn {i}"} for i in range(12)]}, u
        if name == "topic_safety":
            return {"allowed": self.allow, "reason": "ok" if self.allow else "not allowed"}, u
        if name == "lesson_script":
            return make_script(), u
        if name == "quality_check":
            return {"pass": self.qa_pass, "issues": [] if self.qa_pass else ["wrong fact"], "fixed_script": None}, u
        raise AssertionError(name)


class FakeRenderer:
    name = "fake"

    def render(self, params, workdir: Path) -> RenderResult:
        words = params["script"].split()
        video = workdir / "render.mp4"
        video.write_bytes(b"fake-mp4")
        captions = [{"words": [{"word": w, "start": i / 3, "end": (i + 1) / 3} for i, w in enumerate(words)]}]
        return RenderResult(video, {"captions": captions, "duration": len(words) / 3})


class FakeMedia:
    def compress(self, src, dst):
        shutil.copyfile(src, dst)

    def thumbnail(self, src, dst, duration_s):
        Path(dst).write_bytes(b"jpg")


def worker(llm=None, renderer=None, media=None) -> Worker:
    llm = llm or FakeLLM()
    from app.db import session_scope
    from app.services.deletion import process_deletion_job

    def deletion(job_id):
        with session_scope() as db:
            process_deletion_job(db, job_id)

    return Worker(
        "test",
        [
            Route(
                "script-jobs",
                120,
                lambda j: script_pipeline.handle(j, llm),
                script_pipeline.on_syllabus_or_script_failure,
            ),
            Route(
                "render-jobs",
                900,
                lambda j: render_pipeline.handle(j, renderer or FakeRenderer(), media or FakeMedia()),
                render_pipeline.on_render_failure,
            ),
            Route("deletion-jobs", 300, deletion),
        ],
    )


def drain(w: Worker | None = None, max_rounds: int = 200, ignore_delays: bool = True) -> int:
    """Process messages until every queue is empty. Returns the number of messages handled."""
    w = w or worker()
    total = 0
    for _ in range(max_rounds):
        if ignore_delays:
            with get_engine().begin() as conn:
                conn.execute(text("update queue_message set visible_at = now() where visible_at > now()"))
        handled = sum(w.poll_once(r, wait_s=0) for r in w.routes)
        total += handled
        if handled == 0:
            return total
    raise AssertionError("queues did not drain")


def queued(queue: str | None = None) -> int:
    with get_engine().begin() as conn:
        if queue:
            return conn.execute(text("select count(*) from queue_message where queue = :q"), {"q": queue}).scalar()
        return conn.execute(text("select count(*) from queue_message")).scalar()
