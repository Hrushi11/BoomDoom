"""Prompts for the OpenAI film director. The skill's own rules and the engine source go in verbatim, so the model
writes film.js against the real API (vendor/stickman-skill) instead of guessing it."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

IMAGE_STAGES = ("meeting_room", "server_room", "living_room", "museum_hall")  # painted backgrounds (engine/assets.js)
CODE_SETS = ("cafe", "street", "room", "symbolic")  # sets drawn in code (engine/sets/)
VOICE_AT = 0.5  # audio_mix lays vo.wav in at this picture time (m.voice(VOICE, at=.5))
FPS = 30
FRAME_W, FRAME_H = 1080, 1920  # portrait canvas for the 9:16 lesson player (engine patched to read the canvas size)

SCRIPT_SCHEMA = {
    "name": "film_script",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["title", "logline", "set", "sentences"],
        "properties": {
            "title": {"type": "string"},
            "logline": {"type": "string"},
            "set": {"type": "string", "enum": [*IMAGE_STAGES, *CODE_SETS]},
            "sentences": {"type": "array", "items": {"type": "string"}},
        },
    },
}

FILM_SCHEMA = {
    "name": "film_code",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["beats", "world", "shots", "film_js", "sound_block"],
        "properties": {
            "beats": {"type": "array", "items": {"type": "string"}},
            "world": {"type": "string"},
            "shots": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["start_frame", "camera", "action"],
                    "properties": {
                        "start_frame": {"type": "integer"},
                        "camera": {"type": "string"},
                        "action": {"type": "string"},
                    },
                },
            },
            "film_js": {"type": "string"},
            "sound_block": {"type": "string"},
        },
    },
}

FIX_SCHEMA = {
    "name": "film_fix",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["ok", "issues", "film_js"],
        "properties": {
            "ok": {"type": "boolean"},
            "issues": {"type": "array", "items": {"type": "string"}},
            "film_js": {"type": "string"},
        },
    },
}


FILM_S = 30
FILM_STORY_GUIDE = """Choose the one set that fits the topic. Prefer the painted stages:
- meeting_room (whiteboard on the wall): system design, tech, interviews, careers, planning, also finance talks.
- server_room (racks, wall screen): system design, databases, networks, scaling, cloud.
- living_room (sofa, lamp, bookshelf): personal finance, psychology, habits, family and everyday life.
- museum_hall (exhibit panels, column, vase): history, general knowledge, culture, science history.
The older code-drawn sets remain for stories that need them: a café (tables, cups, door, rain), a street (bus stop,
buildings, bus), a room (table, lift, hallway), or a symbolic void (slabs and silhouettes).
Use two characters who interact. Prefer the new cast: an engineer, an interviewer, an analyst, a professor, a student,
a young woman in a kurta, a grandmother in a saree, a museum curator (the older hero, friend, woman, stranger and guests
also exist). Use one physical metaphor built from picture props that fit the topic: laptop, server, database, queue,
load balancer, router, cloud for tech; coins, notes, piggy bank, wallet, card, chart board, calculator, receipt for
money; brain, thought bubble, lightbulb, notebook, hourglass for the mind; scroll, old book, globe, map, compass,
column, pot, crown for history.
Speak directly to the viewer. Open with a relatable moment and explain the idea in conversational language.
Put the visible action in the scene, not in a spoken description of the animation.
Avoid screens full of text and explanations that depend on reading labels; a board may show two or three big words.
Give the last third a new visual beat (a payoff or reveal) and end with a takeaway the viewer can use."""


def script_prompt(topic: str, concept: str, objective: str, words: int) -> str:
    return f"""Write narration for a {FILM_S}-second animated film that teaches one idea to Indian Gen Z.
Topic: {topic}. Concept: {concept}. Objective: {objective or "explain the concept clearly"}.

{FILM_STORY_GUIDE}
Write {words - 6} to {words + 6} words in 7 to 9 short spoken sentences, in plain conversational English.
No specific financial, medical or legal advice.
Return JSON: title (max 40 characters), logline (one sentence a distracted viewer would understand), set, sentences."""


@lru_cache
def _docs(skill: str) -> str:
    root = Path(skill)
    refs = root / "references"
    parts = [
        ("Style bible", refs / "style-bible.md"),
        ("Production notes", refs / "production.md"),
        ("Film craft", refs / "film-craft.md"),
        ("Engine toolkit", refs / "engine.md"),
        ("Sound", refs / "sound.md"),
    ]
    src = [("engine/" + p.name, p) for p in sorted((root / "engine").glob("*.js"))]
    src += [("engine/sets/" + p.name, p) for p in sorted((root / "engine" / "sets").glob("*.js"))]
    text = "\n\n".join(f"## {title}\n{path.read_text()}" for title, path in parts)
    text += "\n\n# ENGINE SOURCE (the only functions that exist)\n"
    text += "\n\n".join(f"// ===== {name} =====\n{path.read_text()}" for name, path in src)
    text += "\n\n# STARTER FILM (working example of every pattern)\n" + (root / "templates" / "film_2d.js").read_text()
    demo = root / "templates" / "film_assets_demo.js"
    if demo.exists():
        text += "\n\n# PAINTED-STAGE EXAMPLE (portrait; stages, picture props, new cast)\n" + demo.read_text()
    text += (
        "\n\n# AUDIO MIX TEMPLATE (you write only the block between the EDIT markers)\n"
        + (root / "templates" / "audio_mix.py").read_text()
    )
    return text


def system_prompt(skill: str) -> str:
    return (
        "Direct and program a short 2D stickman film using the engine below. Write film.js (plain JavaScript "
        "that sets the global FILM and calls say(...)) and the sound block of audio_mix.py. Follow the style "
        "bible and production notes. Use functions and globals defined in the engine source. Define any extra "
        "helpers at the top of film.js. Keep the film deterministic: no Math.random or Date.now. "
        "Return complete, runnable code.\n\n" + _docs(skill)
    )


def timeline(sentences: list[dict]) -> str:
    return "\n".join(
        f"- {s['start'] + VOICE_AT:.2f}s to {s['end'] + VOICE_AT:.2f}s (frames {round((s['start'] + VOICE_AT) * FPS)}-"
        f'{round((s["end"] + VOICE_AT) * FPS)}): "{s["text"]}"'
        for s in sentences
    )


STAGE_NOTES = """
Painted stage rules (see the assets.js section of the engine toolkit): draw the background with stage(x,st) and
stageLife(x,st,t) on the back layer; pass every camera through stageCam(st,cam) (portrait wide shot about
{x:768,y:1250,z:.85}, close shots z 1.2-1.6); place people with stand(st,x,y) or makeWalk(...,scale:stageWalk(st)) on
the stage's floor range and spots; standing props with floorProp (draw the ones behind people first, nearer ones after),
held props with propInHand after actor(); words, diagrams and charts go on the board with boardText, boardDiagram,
barChart (people standing in front of the board hide it, so use spots.left/right beside it). Prefer the new cast
(ENGINEER, INTERVIEWER, ANALYST, PROFESSOR, STUDENT, KURTA, SAREE, CURATOR) and props that fit the topic."""


def film_prompt(script: dict, sentences: list[dict], dur: int, topic: str = "", concept: str = "") -> str:
    where = (
        f"Set: {script['set']}"
        if script.get("set")
        else "Set: choose the one that fits the topic best. Prefer the painted stages (engine/assets.js): meeting_room "
        "or server_room for system design and tech, living_room or meeting_room for finance, living_room for "
        "psychology and habits, museum_hall for history and general knowledge. cafe, street, room and symbolic (for "
        "abstract beats) are also available."
    )
    if script.get("set") in IMAGE_STAGES or not script.get("set"):
        where += STAGE_NOTES
    return f"""Make a VERTICAL film for this narration on a canvas of {FRAME_W}x{FRAME_H} (9:16 portrait).
Topic: {topic}. Concept: {concept}. The film teaches this concept: turn it into visible action and one physical metaphor
(labelled props are fine, e.g. cups marked DATA on a table marked CACHE), even where the narration speaks to the viewer.
Logline: {script["logline"]}
{where}

The narration is already recorded. Picture times (the voice starts at {VOICE_AT}s):
{timeline(sentences)}

Requirements:
- FILM.dur = {dur}. cuts are frames at 30 fps: first 0, last {dur * FPS}. 8 to 11 shots, each 45 frames or longer.
  Cut on or near sentence boundaries so each sentence has its own picture; include a wide shot of the whole world.
- Plan first: beats (action, feeling, camera, sound), ONE world with fixed positions for every character and prop, and a
  shot list. Every shot is only a camera on that world. Keep characters at the same positions and distances across cuts.
- Vertical framing: the camera shows a world area {FRAME_W}/z wide and {FRAME_H}/z tall around (cam.x, cam.y). The
  painted stages are portrait already (keep the camera inside them with stageCam); the code-drawn sets (built for
  landscape) need z >= 1.6 to fill the frame. Frame for portrait: one or two characters filling the
  middle of the frame, close and medium shots, a full-body shot for walks. Keep faces and the key prop in the middle
  60% of the height; the bottom 20% gets captions, so nothing important there.
- The last third of the film needs a new visual beat (a payoff or reveal), not a repeat of an earlier shot.
- At least two characters appear and interact (talk with talk(t,a,b), hand something over, point, react to each other).
- What the narration says must be visible on screen while it is said: characters act it out (walk, sit, sip, turn, talk
  with talk(t,a,b) while their line is spoken, react with brows and mouth). Every shot has motion: breathing, blinks, a
  slow camera move.
- Use that set's functions from the engine source. Draw people with actor(...). Report every held prop with
  contact(...). Hands follow the hand rules.
- sound_block: Python lines that replace the block between the EDIT markers in audio_mix.py. Keep exactly
  `SFX, MUSIC, VOICE = True, True, "vo.wav"`, use beds and effects that fit the set, footfalls from plants, soft
  `m.music(mood=...)` ("warm", "hopeful", "sad" or "tense"), and end with `m.voice(VOICE, at={VOICE_AT})`.
- Use say(t,'name') in film.js for sound events and handle each name in sound_block.

Return JSON: beats, world, shots (start_frame, camera, action), film_js (the complete file), sound_block."""


def load_fix_prompt(film_js: str, errors: list[str]) -> str:
    joined = "\n".join(errors[:20])
    return f"""film.js failed to load or render in the browser. Errors:
{joined}

Fix the code. Use only functions that exist in the engine source. Keep the story, shots, cuts and FILM.dur unchanged.
Return JSON: ok=false, issues (what was wrong), film_js (the complete corrected file).

film.js:
{film_js}"""


def critique_prompt(film_js: str, times: list[float], sentences: list[dict]) -> str:
    stamps = ", ".join(f"{t:.1f}s" for t in times)
    return f"""Review the {len(times)} stills at {stamps} (left to right, top to bottom).
Only cells with a timestamp show film frames. Ignore any unused cells at the end of the grid.
Narration timeline:
{timeline(sentences)}

Check each of those stills against the style bible:
- Does the final third introduce a new visual beat that pays off the lesson?
- Is the action that the narration describes at that moment clearly visible and readable? A still where a character
  just stands with nothing happening is a FAIL: make them act it out.
- Vertical framing: subjects centred and large enough; no empty half of the frame; nothing important in the bottom 20%;
  no edge of the set (empty void) visible at the top or bottom.
- Faces read as the intended emotion (brows not accidentally angry); mouths not deadpan.
- Hands touch what they hold; nothing crosses a face; no floating props; arms bend the right way.
- Characters are fully in frame when they matter, not cut off, not tiny, not duplicated, not overlapping walls wrongly.
- No empty or black frames, no decorative circles, no broken layers.
If everything is good, return ok=true, issues=[], film_js="". Otherwise return ok=false, the issues, and film_js: the
complete corrected file (keep FILM.dur and the cuts unchanged).

film.js:
{film_js}"""


def audit_fix_prompt(film_js: str, issues: list[str]) -> str:
    joined = "\n".join(issues[:30])
    return f"""The rendered film failed the frame audit:
{joined}

Fix film.js so these issues go away (hand reach, contacts, elbows, pops, jumps). Keep FILM.dur, the cuts and the story.
Return JSON: ok=false, issues, film_js (the complete corrected file).

film.js:
{film_js}"""
