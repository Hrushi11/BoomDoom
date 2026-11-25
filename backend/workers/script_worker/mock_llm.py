"""Mock LLM: runs the whole lesson pipeline without an OpenAI key (LLM_MODE=mock).

It answers the same structured calls as OpenAILLM (topic safety, syllabus, lesson script, quality check) with
deterministic, readable content built from the topic and concept in the prompt. Scripts pass the real validator,
so syllabus -> scripts -> renders -> feed behave exactly as with OpenAI, only the words are templated.
No network calls, no cost.
"""

from __future__ import annotations

import re

from workers.script_worker.llm import Usage

BLOCKED = ("bomb", "weapon", "drug synthesis", "self-harm", "porn")

STAGES = [
    ("What it is", "the core idea of {t} in one picture"),
    ("Why it matters", "why {t} shows up in everyday decisions"),
    ("A simple example", "a concrete example of {t}"),
    ("Common myth", "the most common misunderstanding about {t}"),
    ("How it works", "the mechanism behind {t}, step by step"),
    ("Key terms", "the three words you need to talk about {t}"),
    ("When it goes wrong", "what failure looks like in {t}"),
    ("Trade-offs", "the main trade-off people make in {t}"),
    ("Rule of thumb", "a rule of thumb for {t}"),
    ("Real-world case", "a short real-world case of {t}"),
    ("Practice question", "how to reason through a {t} question"),
    ("Next steps", "what to learn after the basics of {t}"),
]


def _find(pattern: str, text: str, default: str) -> str:
    m = re.search(pattern, text)
    return m.group(1).strip() if m else default


def _sentences(topic: str, concept: str, n_words: int) -> list[str]:
    base = [
        f"Here is a quick idea about {topic} that most people miss.",
        f"Today the concept is {concept}.",
        "Picture two friends sitting at a table, trying to make sense of it together.",
        f"The first friend says {concept} sounds complicated, but it is really one simple rule.",
        "The second friend draws it on a napkin, one box at a time.",
        "Start with what you already know, then add one new piece.",
        f"Notice how {concept} changes the outcome once you see the pattern.",
        "Most mistakes happen when we skip that middle step.",
        f"So next time {topic} comes up, slow down and look for this pattern first.",
        "Try explaining it to a friend in one sentence, and you will remember it.",
        "That small habit is how real understanding sticks.",
        "Keep this picture in mind for the next lesson.",
    ]
    out, words, i = [], 0, 0
    while words < n_words:
        s = base[i % len(base)]
        out.append(s)
        words += len(s.split())
        i += 1
    # trim the last sentence so the total lands near the target (validator allows ±15%)
    while words - n_words > 0.08 * n_words and len(out) > 3:
        words -= len(out.pop().split())
    return out


class MockLLM:
    """Drop-in for OpenAILLM.json(model=, prompt=, schema=, temperature=) -> (dict, Usage)."""

    def json(self, *, model: str, prompt: str, schema: dict, temperature: float) -> tuple[dict, Usage]:
        u = Usage("mock", 0, 0)
        name = schema["name"]
        if name == "topic_safety":
            topic = _find(r"Topic: (.+)", prompt, "").lower()
            bad = any(b in topic for b in BLOCKED)
            return {"allowed": not bad, "reason": "not suitable for lessons" if bad else "ok"}, u
        if name == "syllabus":
            topic = _find(r'for "([^"]+)"', prompt, "this topic")
            n = int(_find(r"syllabus of (\d+)", prompt, "12"))
            concepts = []
            for i in range(n):
                title, obj = STAGES[i % len(STAGES)]
                suffix = f" {i // len(STAGES) + 1}" if i >= len(STAGES) else ""
                concepts.append(
                    {"title": f"{topic}: {title.lower()}{suffix}", "objective": "Understand " + obj.format(t=topic)}
                )
            return {"concepts": concepts}, u
        if name == "lesson_script":
            return self._script(prompt), u
        if name == "quality_check":
            return {"pass": True, "issues": [], "fixed_script": None}, u
        raise ValueError(f"mock LLM has no answer for schema {name!r}")

    @staticmethod
    def _script(prompt: str) -> dict:
        topic = _find(r"Topic: ([^.]+)\.", prompt, "this topic")
        level = _find(r"Level: (beginner|intermediate|advanced)", prompt, "beginner")
        language = _find(r"Language: ([^.]+)\.", prompt, "en")
        fmt = _find(r"Format: ([a-z_]+)", prompt, "explainer")
        concept = _find(r"Concept: (.+?) —", prompt, "the basics")
        words = int(_find(r"about (\d+) words", prompt, "90"))
        sents = _sentences(topic, concept, words)
        k = len(sents)
        cuts = [0, k // 3, 2 * k // 3, k]
        segments = [
            {
                "id": f"s{i + 1}",
                "narration": " ".join(sents[cuts[i] : cuts[i + 1]]),
                "scene": ["two friends at a table", "a napkin sketch", "the idea in action"][i],
                "speaker": "narrator",
            }
            for i in range(3)
        ]
        disclaimer = (
            "This is general information, not financial or medical advice."
            if re.search(r"financ|money|invest|health|medic", topic, re.I)
            else None
        )
        return {
            "concept": concept,
            "format": fmt,
            "level": level,
            "language": language,
            "segments": segments,
            "interactions": [
                {
                    "id": "i1",
                    "after_segment": "s1",
                    "type": "predict",
                    "question": "Before the answer: what is this idea mostly about?",
                    "options": ["One simple rule", "Memorising facts", "Pure luck"],
                    "answer_index": 0,
                    "feedback_correct": "Yes: one rule, applied step by step.",
                    "feedback_wrong": "Close, but it comes down to one simple rule.",
                },
                {
                    "id": "i2",
                    "after_segment": "s2",
                    "type": "true_false",
                    "question": "Skipping the middle step is where most mistakes happen.",
                    "options": ["True", "False"],
                    "answer_index": 0,
                    "feedback_correct": "Right. The middle step is where it goes wrong.",
                    "feedback_wrong": "It's true: most mistakes come from skipping it.",
                },
            ],
            "recap": f"{concept}: start from what you know, add one piece, and watch for the pattern in {topic}.",
            "go_deeper": [f"Find one example of {concept} in your day", f"Read a short intro to {topic}"],
            "disclaimer": disclaimer,
        }
