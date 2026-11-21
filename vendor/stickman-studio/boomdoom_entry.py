"""BoomDoom render entry point (headless, no Qt).

Called by BoomDoom's render worker in a subprocess so Stickman Studio runs in its own virtualenv:

    .venv/bin/python boomdoom_entry.py --params params.json --out /tmp/job-dir

params.json keys (Cinematic Illustrated mode): topic, script, script_mode ("use_as_is"), duration, voice_id,
format ("9:16"), cis_visual_style, cis_accent_color, cis_illustration_mode ("pil").

Prints one JSON line on stdout: {"video": "<1080x1920 mp4>", "project": "<project.json with captions[].words>"}.
Exits non-zero on failure. Logs go to stderr.

Environment:
  STICKMAN_MODELS_DIR  Kokoro voice files (default: ./models; expects models/kokoro/*.onnx + voices bin)
  STICKMAN_FONT_DIR    optional caption font directory (default: bundled Noto Sans)
  PATH                 must contain `ffmpeg` (ffprobe optional)
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))


def render_lesson(params: dict, output_dir: str) -> dict:
    from engine.cis_pipeline import CISPipeline
    from tts.tts_manager import TTSManager

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    models_dir = os.environ.get("STICKMAN_MODELS_DIR", str(ROOT / "models"))
    tts = TTSManager(models_dir=models_dir, ffmpeg_path=shutil.which("ffmpeg") or "ffmpeg")
    pipeline = CISPipeline(llm_manager=None, tts_manager=tts, output_dir=str(out), temp_dir=str(out / "tmp"))

    log = logging.getLogger("boomdoom_entry")
    video = pipeline.generate_video(params, log_callback=lambda m: log.info("%s", m))
    if not video or not Path(video).exists():
        raise RuntimeError("Stickman Studio produced no video")
    project = Path(video).parent / "project.json"
    if not project.exists():
        raise RuntimeError(f"project.json missing next to {video}")
    return {"video": str(Path(video).resolve()), "project": str(project.resolve())}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--params", required=True, help="JSON file with render params")
    ap.add_argument("--out", required=True, help="Output directory")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, stream=sys.stderr, format="%(levelname)s %(name)s: %(message)s")
    params = json.loads(Path(args.params).read_text())
    try:
        result = render_lesson(params, args.out)
    except Exception as exc:  # report and fail; the worker retries
        logging.getLogger("boomdoom_entry").exception("render failed: %s", exc)
        return 1
    print(json.dumps(result), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
