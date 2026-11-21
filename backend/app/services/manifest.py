"""Lesson manifest (Tech Spec §7.1): built by the render worker, signed per request by the API."""

from __future__ import annotations

from app.cdn import sign_url
from app.models import Lesson, Topic


def build_manifest(
    lesson: Lesson, script: dict, seg_ends: dict[str, float], pauses: list[dict], duration_s: float, version: int
) -> dict:
    """Stored on the lesson row and in S3. Contains no per-user data and no URLs (signed at read time)."""
    return {
        "lesson_id": str(lesson.id),
        "concept": lesson.concept,
        "format": script["format"],
        "level": lesson.level,
        "language": lesson.language,
        "duration_s": round(duration_s, 2),
        "segments": [{"id": s["id"], "end_s": seg_ends[s["id"]]} for s in script["segments"]],
        "interactions": [
            {
                k: i[k]
                for k in (
                    "id",
                    "after_segment",
                    "pause_at_s",
                    "type",
                    "question",
                    "options",
                    "answer_index",
                    "feedback_correct",
                    "feedback_wrong",
                )
            }
            for i in pauses
        ],
        "recap": script["recap"],
        "go_deeper": script["go_deeper"],
        "disclaimer": script["disclaimer"],
        "version": version,
    }


def signed_manifest(lesson: Lesson, topic: Topic | None) -> dict:
    m = dict(lesson.manifest or {})
    m["lesson_id"] = str(lesson.id)
    m["topic"] = {"id": str(topic.id), "name": topic.name} if topic else None
    if lesson.video_key:
        m["video_url"] = sign_url(lesson.video_key)
    if lesson.thumb_key:
        m["thumb_url"] = sign_url(lesson.thumb_key)
    return m
