from workers.script_worker.schema import validate_script


def _script(words_per_seg=45):
    def seg(i):
        return {
            "id": f"s{i}",
            "narration": " ".join(["word"] * words_per_seg),
            "scene": "x",
            "speaker": "narrator",
        }

    return {
        "concept": "Zeigarnik effect",
        "format": "guess_first",
        "level": "beginner",
        "language": "en",
        "segments": [seg(1), seg(2), seg(3)],
        "interactions": [
            {
                "id": "i1",
                "after_segment": "s1",
                "type": "predict",
                "question": "Why?",
                "options": ["a", "b", "c"],
                "answer_index": 1,
                "feedback_correct": "y",
                "feedback_wrong": "n",
            }
        ],
        "recap": "r",
        "go_deeper": ["more"],
        "disclaimer": None,
    }


def test_valid_script_passes():
    assert validate_script(_script(), target_length_s=45) == []


def test_word_count_and_bad_reference_are_reported():
    s = _script(words_per_seg=10)
    s["interactions"][0]["after_segment"] = "nope"
    issues = validate_script(s, target_length_s=45)
    assert any("words" in i for i in issues)
    assert any("unknown segment" in i for i in issues)


def test_schema_rejects_extra_fields():
    s = _script()
    s["extra"] = 1
    assert validate_script(s, 45)[0].startswith("schema")
