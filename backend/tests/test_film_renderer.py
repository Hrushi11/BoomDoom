from unittest.mock import Mock

import pytest

from app.config import get_settings
from tests.fakes import FakeLLM, make_script
from workers.film_worker import director
from workers.film_worker.compose import caption_pages
from workers.film_worker.prompts import FILM_STORY_GUIDE
from workers.render_worker.pause_times import caption_words, interaction_pauses, segment_end_times
from workers.render_worker.renderers import FilmRenderer, split_sentences
from workers.script_worker.pipeline import ScriptInputs, generate_reviewed_script


def test_split_sentences_preserves_segment_boundaries_and_words():
    segments = [
        {"narration": "  A cup costs 2.50.  Really?\nYes!  "},
        {"narration": "  "},
        {"narration": "Your turn"},
        {"narration": "Next segment."},
    ]
    assert split_sentences(segments) == ["A cup costs 2.50.", "Really?", "Yes!", "Your turn", "Next segment."]


def test_film_renderer_returns_lesson_words_on_picture_clock(monkeypatch, tmp_path):
    # CI can run backend tests in isolation from the renderers and their dependencies.
    studio, skill = tmp_path / "studio", tmp_path / "skill"
    studio.mkdir()
    (studio / "boomdoom_tts.py").touch()
    (skill / "scripts").mkdir(parents=True)
    (skill / "scripts" / "build.py").touch()
    monkeypatch.setattr(get_settings(), "stickman_studio_dir", str(studio))
    monkeypatch.setattr(get_settings(), "stickman_skill_dir", str(skill))
    renderer = FilmRenderer()
    renderer.llm_factory = Mock()
    segments = [{"id": "a", "narration": "Need it again? Look here."}, {"id": "b", "narration": "Reuse it!"}]
    result = director.FilmResult(
        "Caching",
        {},
        [
            {"text": "Need it again?", "start": 0.0, "end": 1.5},
            {"text": "Look here.", "start": 1.78, "end": 2.5},
            {"text": "Reuse it!", "start": 2.78, "end": 4.0},
        ],
        tmp_path / "video.mp4",
        tmp_path / "raw.mp4",
        30,
    )
    make = Mock(return_value=result)
    monkeypatch.setattr(director, "make_film", make)
    rendered = renderer.render(
        {"topic": "fallback", "topic_name": "System Design", "concept": "Caching", "segments": segments}, tmp_path
    )
    make.assert_called_once_with(
        "System Design",
        "Caching",
        tmp_path / "film",
        tools=renderer.tools,
        llm=renderer.llm_factory.return_value,
        narration=["Need it again?", "Look here.", "Reuse it!"],
    )
    assert rendered.video == result.video and rendered.project["duration"] == 30
    words = caption_words(rendered.project)
    assert [w["word"] for w in words] == "Need it again? Look here. Reuse it!".split()
    assert words[0] == {"word": "Need", "start": 0.5, "end": 1.0}
    assert words[3]["start"] == 2.28 and words[4]["end"] == 3.0
    ends, fallback = segment_end_times(segments, words, 30)
    assert (ends, fallback) == ({"a": 3.0, "b": 4.5}, False)
    assert interaction_pauses([{"after_segment": "a"}], ends)[0]["pause_at_s"] == 3.15


def test_long_burned_captions_show_every_word_before_the_sentence_ends():
    sentence = "That’s caching: a system keeps a recent result so it can reuse it instead of asking the source again."
    pages = caption_pages([{"text": sentence, "start": 15.909, "end": 21.370}], width=28)
    assert len(pages) == 2 and pages[1]["text"].endswith("the source again.")
    assert " ".join(page["text"] for page in pages).split() == sentence.split()
    assert all(len(page["text"].splitlines()) <= 3 for page in pages)
    assert pages[0]["start"] == 15.909 and pages[0]["end"] == pages[1]["start"]
    assert pages[1]["end"] == pytest.approx(21.370)


def test_review_samples_both_opening_and_final_shots():
    # Eleven three-second shots, sampled into a nine-frame contact sheet.
    times = director._still_times(list(range(0, 991, 90)))
    assert len(times) == 9
    assert times[0] == 1.5 and times[-1] == 31.5


@pytest.mark.parametrize(
    ("durations", "expected_length", "max_speed"),
    [([25], 30, 1.0), ([30, 29, 28], 30, 1.15), ([40, 35], 37, 1.15)],
)
def test_fixed_narration_fits_before_extending(monkeypatch, tmp_path, durations, expected_length, max_speed):
    narrate = Mock(side_effect=[{"duration": d} for d in durations])
    monkeypatch.setattr(director, "_narrate", narrate)
    words = ["Keep this sentence."]
    _, length = director._fit_given(None, words, tmp_path, [])
    assert length == expected_length
    assert all(call.args[1] == words for call in narrate.call_args_list)
    assert max(call.kwargs["speed"] for call in narrate.call_args_list) == max_speed


@pytest.mark.parametrize(("engine", "seconds"), [("film", 30), ("stickman", 45), ("placeholder", 45)])
def test_engine_controls_script_prompt_validation_and_review(monkeypatch, engine, seconds):
    monkeypatch.setattr(get_settings(), "renderer", engine)
    inp = ScriptInputs(
        None,
        "System Design",
        "beginner",
        None,
        "hi",
        "story",
        {"title": "Caching", "objective": "Reuse a result"},
        [],
        [],
        45,
    )
    llm = FakeLLM()
    calls = []

    def respond(**kw):
        data, usage = llm.json(**kw)
        calls.append(kw["prompt"])
        if kw["schema"]["name"] == "lesson_script":
            return make_script(seconds), usage
        return data, usage

    script, _ = generate_reviewed_script(Mock(json=respond), inp)
    assert script == make_script(seconds)
    prompt, review = calls
    assert f"about {seconds * 3} words" in prompt
    assert (FILM_STORY_GUIDE in prompt) == (engine == "film")
    assert "Language: hi" in prompt and "Format: story" in prompt
    assert (FILM_STORY_GUIDE in review) == (engine == "film")
    assert f"target narration length is {seconds * 3} words" in review
