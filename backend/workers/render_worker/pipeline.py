"""Render jobs (Tech Spec §8.8): render -> pause times -> compress + thumbnail -> S3 -> manifest -> ready."""

from __future__ import annotations

import json
import logging
import shutil
import tempfile
import time
import uuid
from pathlib import Path

from app import metrics
from app.config import get_settings
from app.db import session_scope
from app.domain.lifecycle import LessonStatus
from app.models import AppUser, Job, Lesson
from app.queues import utcnow
from app.services import generation, push
from app.services.manifest import build_manifest
from app.storage import get_storage, lesson_prefix
from workers.common import PermanentError
from workers.render_worker.media import Media
from workers.render_worker.pause_times import caption_words, interaction_pauses, segment_end_times
from workers.render_worker.renderers import Renderer
from workers.script_worker.pipeline import fail_lesson

log = logging.getLogger(__name__)


def handle(job_id: uuid.UUID, renderer: Renderer, media: Media) -> None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        lesson = db.get(Lesson, job.lesson_id) if job and job.lesson_id else None
        if lesson is None:
            return
        if lesson.status == LessonStatus.READY.value:
            return  # duplicate
        if lesson.script is None:
            raise PermanentError("lesson has no script")
        lesson.status = LessonStatus.RENDERING.value
        script = lesson.script
        version = int((lesson.manifest or {}).get("version", 0)) + 1
        lesson_id = lesson.id
        params = {
            "topic": lesson.concept,
            "script": " ".join(s["narration"].strip() for s in script["segments"]),
            "script_mode": "use_as_is",
            "duration": generation.profile_for(db, job.user_id).length_s if job.user_id else 45,
            "voice_id": lesson.voice_id,
            "format": "9:16",
            "cis_visual_style": lesson.visual_style,
            "cis_accent_color": lesson.accent_color,
            "cis_illustration_mode": "pil",
        }

    root = Path(get_settings().render_tmp_dir)
    root.mkdir(parents=True, exist_ok=True)
    workdir = Path(tempfile.mkdtemp(prefix=f"{lesson_id}-", dir=root))
    started = time.monotonic()
    try:
        result = renderer.render(params, workdir)
        render_s = time.monotonic() - started
        words = caption_words(result.project)
        shots_end = max((float(sh.get("end", 0)) for sh in result.project.get("shots", [])), default=0.0)
        duration = float(
            result.project.get("duration") or shots_end or (words[-1]["end"] if words else params["duration"])
        )
        seg_ends, fallback = segment_end_times(script["segments"], words, duration)
        if fallback:
            metrics.emit("pause_alignment_fallback", 1)
        pauses = interaction_pauses(script["interactions"], seg_ends)

        out, thumb = workdir / "lesson_720p.mp4", workdir / "thumb.jpg"
        media.compress(result.video, out)
        media.thumbnail(out, thumb, duration)

        prefix = lesson_prefix(lesson_id, version)
        storage = get_storage()
        with session_scope() as db:
            lesson = db.get(Lesson, lesson_id)
            if lesson is None:
                return  # deleted while rendering
            manifest = build_manifest(lesson, script, seg_ends, pauses, duration, version)
        storage.put_file(prefix + "lesson_720p.mp4", out, "video/mp4")
        storage.put_file(prefix + "thumb.jpg", thumb, "image/jpeg")
        storage.put_bytes(prefix + "manifest.json", json.dumps(manifest).encode(), "application/json")
        storage.put_bytes(prefix + "project.json", json.dumps(result.project).encode(), "application/json")
        _mark_ready(lesson_id, manifest, prefix, duration, render_s)
        metrics.emit("render_seconds", round(render_s, 1), "Seconds", renderer=renderer.name)
        metrics.emit("lesson_ready", 1)
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def _mark_ready(lesson_id: uuid.UUID, manifest: dict, prefix: str, duration: float, render_s: float) -> None:
    notify: list[tuple[str | None, str]] = []
    with session_scope() as db:
        lesson = db.get(Lesson, lesson_id)
        if lesson is None:
            return
        lesson.manifest, lesson.duration_s, lesson.render_seconds = manifest, duration, round(render_s, 1)
        lesson.video_key, lesson.thumb_key = prefix + "lesson_720p.mp4", prefix + "thumb.jpg"
        lesson.status, lesson.ready_at = LessonStatus.READY.value, utcnow()
        for topic in generation.topics_for_lesson(db, lesson.id):
            old, new = generation.refresh_topic_status(db, topic)
            if old == "preparing" and new == "ready":
                user = db.get(AppUser, topic.user_id)
                notify.append((user.fcm_token if user else None, str(topic.id)))
    for token, topic_id in notify:  # after commit; best effort
        push.lessons_ready(token, topic_id)


def on_render_failure(job_id: uuid.UUID, error: str) -> None:
    with session_scope() as db:
        job = db.get(Job, job_id)
        if job and job.lesson_id:
            fail_lesson(db, job.lesson_id)
