"""Real ffmpeg render path: placeholder render -> 720p compress -> thumbnail -> manifest -> feed."""

import shutil
import subprocess

import pytest

from tests.fakes import drain, worker
from workers.render_worker.media import Media
from workers.render_worker.renderers import PlaceholderRenderer


def _ffmpeg() -> str | None:
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return shutil.which("ffmpeg")


FFMPEG = _ffmpeg()
pytestmark = pytest.mark.skipif(FFMPEG is None, reason="ffmpeg not available")


def _probe(path) -> str:
    out = subprocess.run([FFMPEG, "-hide_banner", "-i", str(path)], capture_output=True, text=True).stderr
    return out


def test_placeholder_pipeline_produces_720p_lesson(api):
    from pathlib import Path

    from app.config import get_settings

    api.profile({"name": "Psychology", "level": "beginner", "type": "preset"})
    drain(worker(renderer=PlaceholderRenderer(FFMPEG), media=Media(FFMPEG)))
    feed = api.get("/v1/feed").json()
    assert len(feed) == 5
    lesson = feed[0]
    assert 44 <= lesson["duration_s"] <= 46  # 135 words at 3 words/s

    store = Path(get_settings().local_storage_dir)
    video = next(store.glob(f"lessons/{lesson['lesson_id']}/v1/lesson_720p.mp4"))
    info = _probe(video)
    assert "720x1280" in info and "h264" in info and "aac" in info
    thumb = video.with_name("thumb.jpg")
    assert thumb.stat().st_size > 0 and "360x640" in _probe(thumb)
