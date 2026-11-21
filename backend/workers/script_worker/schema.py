"""Lesson script JSON Schema for OpenAI Structured Outputs (Tech Spec Appendix A) + post-validation."""

import jsonschema

LESSON_SCRIPT_SCHEMA: dict = {
    "name": "lesson_script",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": [
            "concept",
            "format",
            "level",
            "language",
            "segments",
            "interactions",
            "recap",
            "go_deeper",
            "disclaimer",
        ],
        "properties": {
            "concept": {"type": "string"},
            "format": {
                "type": "string",
                "enum": ["explainer", "myth_fact", "story", "dialogue", "guess_first", "series"],
            },
            "level": {"type": "string", "enum": ["beginner", "intermediate", "advanced"]},
            "language": {"type": "string"},
            "segments": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["id", "narration", "scene", "speaker"],
                    "properties": {
                        "id": {"type": "string"},
                        "narration": {"type": "string"},
                        "scene": {"type": "string"},
                        "speaker": {"type": "string", "enum": ["narrator", "speaker1", "speaker2"]},
                    },
                },
            },
            "interactions": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": [
                        "id",
                        "after_segment",
                        "type",
                        "question",
                        "options",
                        "answer_index",
                        "feedback_correct",
                        "feedback_wrong",
                    ],
                    "properties": {
                        "id": {"type": "string"},
                        "after_segment": {"type": "string"},
                        "type": {
                            "type": "string",
                            "enum": ["predict", "mcq", "true_false", "reflect", "your_answer", "go_deeper"],
                        },
                        "question": {"type": "string"},
                        "options": {"type": "array", "items": {"type": "string"}},
                        "answer_index": {"type": ["integer", "null"]},
                        "feedback_correct": {"type": "string"},
                        "feedback_wrong": {"type": "string"},
                    },
                },
            },
            "recap": {"type": "string"},
            "go_deeper": {"type": "array", "items": {"type": "string"}},
            "disclaimer": {"type": ["string", "null"]},
        },
    },
}

WORDS_PER_SECOND = 3


def validate_script(script: dict, target_length_s: int) -> list[str]:
    """Structural checks the strict schema can't express. Returns a list of problems (empty = ok)."""
    try:
        jsonschema.validate(script, LESSON_SCRIPT_SCHEMA["schema"])
    except jsonschema.ValidationError as e:
        return [f"schema: {e.message}"]

    issues: list[str] = []
    segs, inter = script["segments"], script["interactions"]
    if not 3 <= len(segs) <= 6:
        issues.append(f"expected 3-6 segments, got {len(segs)}")
    if not 1 <= len(inter) <= 3:
        issues.append(f"expected 1-3 interactions, got {len(inter)}")
    if len(script["go_deeper"]) > 3:
        issues.append("at most 3 go_deeper items")

    seg_ids = {s["id"] for s in segs}
    for i in inter:
        if i["after_segment"] not in seg_ids:
            issues.append(f"interaction {i['id']} references unknown segment {i['after_segment']}")
        if len(i["options"]) > 4:
            issues.append(f"interaction {i['id']} has more than 4 options")
        if i["type"] in {"predict", "mcq", "true_false"}:
            ai = i["answer_index"]
            if ai is None or not 0 <= ai < len(i["options"]):
                issues.append(f"interaction {i['id']} needs a valid answer_index")

    words = sum(len(s["narration"].split()) for s in segs)
    target = target_length_s * WORDS_PER_SECOND
    if abs(words - target) > 0.15 * target:
        issues.append(f"narration has {words} words, target {target} ±15%")
    return issues
