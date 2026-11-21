"""Pause-time extraction from Stickman Studio's project.json (Tech Spec §8.8)."""

import logging
import re

log = logging.getLogger(__name__)
PAUSE_PAD_S = 0.15
MAX_MISMATCH = 0.10


def _tok(w: str) -> str:
    return re.sub(r"[^\w]", "", w.lower())


def caption_words(project: dict) -> list[dict]:
    return [w for c in project.get("captions", []) for w in c.get("words", [])]


def segment_end_times(segments: list[dict], words: list[dict], duration_s: float) -> tuple[dict[str, float], bool]:
    """Map segment id -> end time of its last narrated word. Returns (ends, used_fallback).

    Walks narration words in order against the caption word stream. If more than 10% of tokens
    don't line up, falls back to proportional timing by word count.
    """
    seg_tokens = [[_tok(w) for w in s["narration"].split() if _tok(w)] for s in segments]
    stream = [(_tok(w["word"]), float(w["end"])) for w in words if _tok(w["word"])]

    ends: dict[str, float] = {}
    pos, mismatches, total = 0, 0, sum(len(t) for t in seg_tokens)
    for seg, toks in zip(segments, seg_tokens, strict=True):
        last_end = None
        for t in toks:
            # small look-ahead tolerates TTS splitting/merging tokens
            j = next((k for k in range(pos, min(pos + 3, len(stream))) if stream[k][0] == t), None)
            if j is None:
                mismatches += 1
                continue
            last_end, pos = stream[j][1], j + 1
        if last_end is not None:
            ends[seg["id"]] = last_end

    if total == 0 or mismatches / total > MAX_MISMATCH or len(ends) != len(segments):
        log.warning("caption alignment poor (%d/%d mismatched); using proportional timing", mismatches, total)
        acc, ends = 0, {}
        for seg, toks in zip(segments, seg_tokens, strict=True):
            acc += len(toks)
            ends[seg["id"]] = round(duration_s * acc / max(total, 1), 3)
        return ends, True
    return ends, False


def interaction_pauses(interactions: list[dict], seg_ends: dict[str, float]) -> list[dict]:
    return [{**i, "pause_at_s": round(seg_ends[i["after_segment"]] + PAUSE_PAD_S, 3)} for i in interactions]
