from app.domain.sharing import is_shareable, next_format, normalize_topic, share_key, shortfall


def test_normalize_topic_dedupes_case_and_punctuation():
    assert normalize_topic("  Psychology!! ") == normalize_topic("psychology")
    assert normalize_topic("Interview   Prep") == "interview prep"


def test_only_preset_topics_without_goal_are_shared():
    assert is_shareable("preset", None)
    assert not is_shareable("preset", "crack my Amazon interview")
    assert not is_shareable("custom", None)


def test_share_key_is_stable_and_sensitive_to_voice():
    a = share_key("psychology", "beginner", "en", "af_heart", "B1")
    assert a == share_key("psychology", "beginner", "en", "af_heart", "B1")
    assert a != share_key("psychology", "beginner", "en", "am_adam", "B1")


def test_shortfall_refills_to_target_only_below_threshold():
    assert shortfall(0) == 5
    assert shortfall(2) == 3
    assert shortfall(3) == 0
    assert shortfall(7) == 0


def test_format_rotation_skips_high_skip_formats():
    assert next_format(0) == "explainer"
    assert next_format(0, {"explainer"}) == "guess_first"
    assert next_format(3, set(["explainer", "guess_first", "myth_fact", "story", "dialogue"])) == "story"
