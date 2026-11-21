"""9:16 cut of a 16:9 film for the lesson player: the film sharp in the middle, a blurred, darkened copy filling the
frame behind it, the title above and the narration as captions below (readable with the sound off)."""

from __future__ import annotations

import shutil
import subprocess
import textwrap
from pathlib import Path

W, H = 1080, 1920
FILM_H = 608  # 1080 wide at 16:9
FILM_Y = 620
TITLE_Y = 360
CAPTION_Y = FILM_Y + FILM_H + 90


def _font(font_dir: Path, bold: bool) -> str:
    for name in (["NotoSans-Bold.ttf", "NotoSans-SemiBold.ttf"] if bold else []) + [
        "NotoSans-Regular.ttf",
        "NotoSans.ttf",
    ]:
        if (font_dir / name).exists():
            return str(font_dir / name)
    found = sorted(font_dir.glob("*.ttf")) if font_dir.exists() else []
    if found:
        return str(found[0])
    raise FileNotFoundError(f"no .ttf font in {font_dir}")


def _q(path: Path) -> str:
    """A path inside an ffmpeg filter argument."""
    return str(path).replace("\\", "/").replace(":", "\\:").replace("'", "\\'")


def caption_pages(captions: list[dict], width: int) -> list[dict]:
    """Break long sentences into three-line pages on the same word-length clock as the project captions."""
    pages = []
    for caption in captions:
        words = caption["text"].split()
        if not words:
            continue
        lines = textwrap.wrap(" ".join(words), width, break_long_words=False, break_on_hyphens=False)
        total = sum(len(word) + 1 for word in words)
        span = caption["end"] - caption["start"]
        start, consumed = caption["start"], 0
        for index in range(0, len(lines), 3):
            text = "\n".join(lines[index : index + 3])
            consumed += sum(len(word) + 1 for word in text.split())
            end = caption["start"] + span * consumed / total
            pages.append({"text": text, "start": start, "end": end})
            start = end
    return pages


def compose_vertical(
    src: Path, dst: Path, title: str, captions: list[dict], font_dir: Path, ffmpeg: str | None = None
) -> Path:
    ff = ffmpeg or shutil.which("ffmpeg") or "ffmpeg"
    probe = subprocess.run([ff, "-hide_banner", "-i", str(src)], capture_output=True, text=True).stderr
    if " 1080x1920" in probe:  # already a 9:16 film: burn in captions and a short title, keep the full frame
        return _caption_portrait(ff, src, dst, title, captions, font_dir)
    tmp = dst.parent / "captions"
    tmp.mkdir(parents=True, exist_ok=True)
    bold, regular = _font(font_dir, True), _font(font_dir, False)

    (tmp / "title.txt").write_text("\n".join(textwrap.wrap(title, 24)[:2]))
    filters = [
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=28:2,eq=brightness=-0.22[bg]",
        f"[0:v]scale={W}:{FILM_H}[fg]",
        f"[bg][fg]overlay=0:{FILM_Y}[v0]",
        f"[v0]drawtext=fontfile='{_q(Path(bold))}':textfile='{_q(tmp / 'title.txt')}':expansion=none:"
        f"fontsize=64:fontcolor=white:"
        f"line_spacing=14:x=(w-text_w)/2:y={TITLE_Y}[v1]",
    ]
    last = "v1"
    for i, c in enumerate(caption_pages(captions, 30)):
        f = tmp / f"c{i:02d}.txt"
        f.write_text(c["text"])
        nxt = f"v{i + 2}"
        filters.append(
            f"[{last}]drawtext=fontfile='{_q(Path(regular))}':textfile='{_q(f)}':expansion=none:fontsize=50:fontcolor=white:"
            f"line_spacing=12:x=(w-text_w)/2:y={CAPTION_Y}:box=1:boxcolor=black@0.35:boxborderw=18:"
            f"enable='gte(t,{c['start']:.3f})*lt(t,{c['end']:.3f})'[{nxt}]"
        )
        last = nxt
    subprocess.run(
        [
            ff,
            "-y",
            "-loglevel",
            "error",
            "-i",
            str(src),
            "-filter_complex",
            ";".join(filters),
            "-map",
            f"[{last}]",
            "-map",
            "0:a?",
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "21",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "160k",
            "-movflags",
            "+faststart",
            str(dst),
        ],
        check=True,
    )
    shutil.rmtree(tmp, ignore_errors=True)
    return dst


def _caption_portrait(ff: str, src: Path, dst: Path, title: str, captions: list[dict], font_dir: Path) -> Path:
    tmp = dst.parent / "captions"
    tmp.mkdir(parents=True, exist_ok=True)
    bold, regular = _font(font_dir, True), _font(font_dir, False)
    (tmp / "title.txt").write_text("\n".join(textwrap.wrap(title, 22)[:2]))
    filters = [
        f"[0:v]drawtext=fontfile='{_q(Path(bold))}':textfile='{_q(tmp / 'title.txt')}':expansion=none:"
        f"fontsize=68:fontcolor=white:"
        f"line_spacing=14:x=(w-text_w)/2:y=220:shadowcolor=black@0.55:shadowx=0:shadowy=4:"
        f"alpha='if(lt(t,2.4),1,max(0,1-(t-2.4)/0.5))':enable='lt(t,2.9)'[v0]"
    ]
    last = "v0"
    for i, c in enumerate(caption_pages(captions, 28)):
        f = tmp / f"c{i:02d}.txt"
        f.write_text(c["text"])
        nxt = f"v{i + 1}"
        filters.append(
            f"[{last}]drawtext=fontfile='{_q(Path(regular))}':textfile='{_q(f)}':expansion=none:fontsize=52:fontcolor=white:"
            f"line_spacing=12:x=(w-text_w)/2:y=h-text_h-230:box=1:boxcolor=black@0.4:boxborderw=20:"
            f"enable='gte(t,{c['start']:.3f})*lt(t,{c['end']:.3f})'[{nxt}]"
        )
        last = nxt
    subprocess.run(
        [
            ff,
            "-y",
            "-loglevel",
            "error",
            "-i",
            str(src),
            "-filter_complex",
            ";".join(filters),
            "-map",
            f"[{last}]",
            "-map",
            "0:a?",
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "21",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "160k",
            "-movflags",
            "+faststart",
            str(dst),
        ],
        check=True,
    )
    shutil.rmtree(tmp, ignore_errors=True)
    return dst
