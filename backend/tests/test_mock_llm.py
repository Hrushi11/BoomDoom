"""LLM_MODE=mock: the full pipeline (syllabus -> scripts -> renders -> feed) runs without OpenAI."""

from tests.fakes import drain, worker
from workers.script_worker.mock_llm import MockLLM
from workers.script_worker.schema import validate_script


def test_mock_scripts_pass_the_real_validator():
    llm = MockLLM()
    for words in (90, 135, 180):
        prompt = (
            "Topic: Personal Finance. Level: beginner. Goal: none. Language: en. Format: explainer.\n"
            f"Concept: Personal Finance: why it matters — objective: x.\nRules: about {words} words of narration"
        )
        script, _ = llm.json(model="m", prompt=prompt, schema={"name": "lesson_script"}, temperature=1)
        assert validate_script(script, words // 3) == []
        assert script["disclaimer"]  # finance topics carry a disclaimer


def test_mock_mode_fills_the_feed(make_api):
    api = make_api()
    api.profile({"name": "System Design", "level": "beginner", "type": "custom"})
    drain(worker(llm=MockLLM()))
    feed = api.get("/v1/feed").json()
    assert feed and all(item["concept"].startswith("System Design:") for item in feed)


def test_auto_mode_uses_the_mock_without_a_key(monkeypatch):
    from app.config import get_settings

    s = get_settings()
    monkeypatch.setattr(s, "llm_mode", "auto")
    monkeypatch.setattr(s, "openai_api_key", "")
    assert s.use_mock_llm
    monkeypatch.setattr(s, "openai_api_key", "sk-test")
    assert not s.use_mock_llm
