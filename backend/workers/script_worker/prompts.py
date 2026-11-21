"""Initial prompts (Tech Spec Appendix B). Keep versions in sync with the doc."""

QA_RUBRIC = """\
1. Factually correct for a general audience; no fabricated statistics, names or dates.
2. Exactly one main idea; hook in the first sentence.
3. Narration word count within ±15% of target.
4. At least one interaction; each interaction has exactly one correct answer supported by the narration.
5. No specific financial, medical or legal advice; standard disclaimer for finance/health topics.
6. Narration uses concrete, visual nouns (the animator picks illustrations from the text).
7. Language and register match the profile (e.g., simple English; Hinglish if requested)."""


def syllabus_prompt(topic: str, level: str, goal: str | None, n: int = 25) -> str:
    goal_part = f", aimed at: {goal}" if goal else ""
    return (
        f'Create an ordered syllabus of {n} micro-concepts for "{topic}" at {level} level for Indian Gen Z '
        f"learners{goal_part}. Each concept must be teachable in a 30–60 second lesson with one main idea. "
        "Order from foundational to advanced. Avoid overlap. Return JSON: {concepts:[{title, objective}]}."
    )


def script_prompt(
    *,
    topic: str,
    level: str,
    goal: str | None,
    language: str,
    fmt: str,
    title: str,
    objective: str,
    covered: list[str],
    weak: list[str],
    words: int,
) -> str:
    return f"""You write scripts for 30–60 s vertical stickman micro-lessons for Indian Gen Z.
Topic: {topic}. Level: {level}. Goal: {goal or "none"}. Language: {language}. Format: {fmt}.
Concept: {title} — objective: {objective}. Already covered: {", ".join(covered) or "none"}. \
Revisit if natural: {", ".join(weak) or "none"}.
Rules: about {words} words of narration in total (~3 words per second); hook in the first sentence; one idea \
only; at least one interaction every 15–20 s; simple, conversational language; concrete, visual nouns (the \
animator picks illustrations from them); one-line scene hint per segment; no specific financial, medical or \
legal advice; add a one-line disclaimer for finance/health topics.
Return JSON matching the schema."""


def qa_prompt(script_json: str) -> str:
    return (
        f"Check this lesson script against the rubric:\n{QA_RUBRIC}\n\nScript:\n{script_json}\n\n"
        "Return JSON {pass: boolean, issues: [string], fixed_script: <schema or null>}. "
        "Fix only small issues; set fixed_script to null for factual problems."
    )


def safety_prompt(topic: str, goal: str | None) -> str:
    return (
        "You screen topics for a micro-learning app for Indian Gen Z (16+). Decide whether short educational "
        "lessons can responsibly be made on this topic. Disallow sexual content, self-harm methods, weapons or "
        "drug synthesis, hate, harassment of real people, and instructions for illegal activity. Allow sensitive "
        "but educational subjects (history of wars, mental-health awareness, personal finance).\n"
        f"Topic: {topic}\nGoal: {goal or 'none'}\nReturn JSON {{allowed: boolean, reason: string}}."
    )
