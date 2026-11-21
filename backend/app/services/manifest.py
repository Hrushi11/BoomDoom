"""Builds the client-facing lesson manifest (Tech Spec §7.1) with signed CDN URLs."""

from datetime import UTC, datetime, timedelta

from app.config import get_settings
from app.models import Lesson

SIGNED_URL_TTL = timedelta(hours=24)


def sign_url(key: str) -> str:
    """CloudFront signed URL in AWS; plain URL locally. TODO: CloudFront key-group signing (§9.2)."""
    base = get_settings().cdn_base_url.rstrip("/")
    expires = int((datetime.now(UTC) + SIGNED_URL_TTL).timestamp())
    return f"{base}/{key}?Expires={expires}"


def signed_manifest(lesson: Lesson) -> dict:
    manifest = dict(lesson.manifest or {})
    manifest["lesson_id"] = str(lesson.id)
    if lesson.video_key:
        manifest["video_url"] = sign_url(lesson.video_key)
    if lesson.thumb_key:
        manifest["thumb_url"] = sign_url(lesson.thumb_key)
    return manifest
