"""Lesson sharing and stock rules (Tech Spec §8.6)."""

import hashlib
import re
import unicodedata

FORMAT_MIX_VERSION = "1"
FORMAT_ROTATION = ["explainer", "guess_first", "myth_fact", "story", "dialogue"]


def normalize_topic(name: str) -> str:
    """Lower-case, strip accents/punctuation, collapse whitespace. Used for dedupe and sharing."""
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode() or name
    s = re.sub(r"[^\w\s]", " ", s.lower())
    return re.sub(r"\s+", " ", s).strip()


def is_shareable(topic_type: str, goal: str | None) -> bool:
    """Only preset topics without a goal produce shared lessons."""
    return topic_type == "preset" and not (goal and goal.strip())


def share_key(
    topic_norm: str,
    level: str,
    language: str,
    voice_id: str,
    visual_style: str,
    format_mix_version: str = FORMAT_MIX_VERSION,
) -> str:
    raw = "|".join([topic_norm, level, language, voice_id, visual_style, format_mix_version])
    return hashlib.sha256(raw.encode()).hexdigest()


def shortfall(unwatched_ready: int, target: int = 5, refill_below: int = 3) -> int:
    """How many lessons to generate for a (user, topic). Refill only once stock drops below the threshold."""
    if unwatched_ready >= refill_below:
        return 0
    return max(0, target - unwatched_ready)


def next_format(index: int, high_skip_formats: set[str] | None = None) -> str:
    """Rotate formats, skipping ones this user tends to skip (falls back to plain rotation)."""
    skip = high_skip_formats or set()
    candidates = [f for f in FORMAT_ROTATION if f not in skip] or FORMAT_ROTATION
    return candidates[index % len(candidates)]
