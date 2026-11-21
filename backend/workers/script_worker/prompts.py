"""Initial prompts (Tech Spec Appendix B). Keep versions in sync with the doc."""

from workers.film_worker.prompts import FILM_S, FILM_STORY_GUIDE

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


STUDIO_STORY_GUIDE = """Make it a little story the animator can draw (it picks illustrations from the words you say):
- Open on one relatable moment the viewer can picture: a time, a place and an object, with "you" as the stick \
figure (e.g. "It's 2 AM in your hostel room and your phone buzzes…"). Everyday Indian settings: hostel, metro, \
chai stall, exam hall, office stand-up, family WhatsApp group.
- Explain the idea through one physical metaphor that returns across segments (a bouncer at a door, a leaky \
bucket, a traffic signal, a tug of war) instead of abstract jargon; name its parts as concrete objects.
- Each segment shows something different happening (a change, a contrast, a reveal), never just a definition.
- End on a small, practical takeaway the viewer can do today."""


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
    film: bool = False,
) -> str:
    duration = f"{FILM_S} s" if film else "30–60 s"
    story = (
        f"{FILM_STORY_GUIDE}\nSpread 7 to 9 short spoken sentences across 3 to 6 lesson segments."
        if film
        else STUDIO_STORY_GUIDE
    )
    return f"""You write scripts for {duration} vertical stickman micro-lessons for Indian Gen Z.
Topic: {topic}. Level: {level}. Goal: {goal or "none"}. Language: {language}. Format: {fmt}.
Concept: {title} — objective: {objective}. Already covered: {", ".join(covered) or "none"}. \
Revisit if natural: {", ".join(weak) or "none"}.
Rules: about {words} words of narration in total (~3 words per second); hook in the first sentence; one idea \
only; at least one interaction every 15–20 s; simple, conversational language; one-line scene hint per segment; \
no specific financial, medical or legal advice; add a one-line disclaimer for finance/health topics.
{story}
Return JSON matching the schema."""


def qa_prompt(script_json: str, target_words: int, language: str = "en", *, film: bool = False) -> str:
    guidance = f"\nFilm direction to preserve in any edits:\n{FILM_STORY_GUIDE}\n" if film else ""
    return (
        f"Check this lesson script against the rubric:\n{QA_RUBRIC}\n{guidance}\n"
        f"Context for the rubric: target narration length is {target_words} words in total across all segments "
        f"(acceptable range {int(target_words * 0.85)}–{int(target_words * 1.15)}); profile language: {language}.\n\n"
        f"Script:\n{script_json}\n\n"
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
