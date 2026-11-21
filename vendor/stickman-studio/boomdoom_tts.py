"""BoomDoom narration entry point: Stickman Studio's Kokoro voice, one sentence at a time (headless, no Qt).

Used by the film worker so the picture can be cut to the voice:

    .venv/bin/python boomdoom_tts.py --params params.json --out /tmp/film

params.json: {"sentences": ["...", ...], "voice_id": "af_heart", "speed": 1.0, "gap_s": 0.28}

Writes <out>/vo.wav (mono, 24 kHz, 16-bit) and prints one JSON line:
{"voice": "<path>", "duration": 27.4, "sentences": [{"text": "...", "start": 0.0, "end": 3.1}, ...]}
Times are seconds from the start of vo.wav. Exits non-zero on failure; logs go to stderr.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import shutil
import subprocess
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))
RATE = 24000


def _ffmpeg() -> str:
    return shutil.which("ffmpeg") or "ffmpeg"


def _normalise(src: Path, dst: Path) -> None:
    """Any TTS output -> mono 24 kHz s16 WAV, so the pieces can be joined sample-exactly."""
    subprocess.run([_ffmpeg(), "-y", "-loglevel", "error", "-i", str(src), "-ac", "1", "-ar", str(RATE),
                    "-sample_fmt", "s16", str(dst)], check=True)


def narrate(params: dict, out: Path) -> dict:
    from tts.tts_manager import TTSManager

    out.mkdir(parents=True, exist_ok=True)
    tmp = out / "tts_parts"
    tmp.mkdir(exist_ok=True)
    tts = TTSManager(models_dir=os.environ.get("STICKMAN_MODELS_DIR", str(ROOT / "models")), ffmpeg_path=_ffmpeg())
    voice, speed = str(params.get("voice_id") or "af_heart"), float(params.get("speed", 1.0))
    gap = int(float(params.get("gap_s", 0.28)) * RATE)
    frames, timings, cursor = [], [], 0
    for i, text in enumerate(s.strip() for s in params["sentences"]):
        if not text:
            continue
        raw = tts.synthesize(text, voice, speed=speed, output_path=str(tmp / f"raw_{i:02d}.wav"))
        if not raw or not Path(raw).exists():
            raise RuntimeError(f"TTS failed for sentence {i}: {text[:60]!r}")
        norm = tmp / f"s_{i:02d}.wav"
        _normalise(Path(raw), norm)
        with wave.open(str(norm), "rb") as w:
            data = w.readframes(w.getnframes())
        n = len(data) // 2
        if timings:
            frames.append(b"\x00\x00" * gap)
            cursor += gap
        timings.append({"text": text, "start": round(cursor / RATE, 3), "end": round((cursor + n) / RATE, 3)})
        frames.append(data)
        cursor += n
    if not timings:
        raise RuntimeError("no sentences to speak")
    vo = out / "vo.wav"
    with wave.open(str(vo), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(frames))
    shutil.rmtree(tmp, ignore_errors=True)
    return {"voice": str(vo.resolve()), "duration": round(cursor / RATE, 3), "sentences": timings}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--params", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, stream=sys.stderr, format="%(levelname)s %(name)s: %(message)s")
    try:
        result = narrate(json.loads(Path(args.params).read_text()), Path(args.out))
    except Exception as exc:  # report and fail; the worker retries
        logging.getLogger("boomdoom_tts").exception("narration failed: %s", exc)
        return 1
    print(json.dumps(result), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
