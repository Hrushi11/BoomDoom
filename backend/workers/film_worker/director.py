"""OpenAI-directed stickman film, following vendor/stickman-skill's pipeline (SKILL.md steps 3-6) without a human:

1. Script: use the approved lesson narration, or generate a standalone script.
2. Voice: Stickman Studio's Kokoro, one sentence at a time, so every sentence has exact times (boomdoom_tts.py).
3. Plan + code: beats, one world, 8-11 shots cut to the sentences, film.js and the sound block (OpenAI).
4. Load check: the film must load and render in headless Chromium; errors go back to the model.
5. Stills critique: a contact sheet at the middle of every shot, reviewed by the model with vision.
6. Build: render every shot, mix voice + effects + soft music, frame audit; audit issues get one fix round.
7. Compose: burn the title and timed captions into the portrait film for the lesson player.

Runs without a database (local tests: FilmLLM(record_spend=False)).
"""

from __future__ import annotations

import json
import logging
import math
import os
import re
import shutil
import subprocess
from collections.abc import Callable
from dataclasses import dataclass, field
from itertools import pairwise
from pathlib import Path

from workers.film_worker import prompts
from workers.film_worker.compose import compose_vertical
from workers.film_worker.llm import FilmLLM
from workers.film_worker.prompts import FILM_S

log = logging.getLogger(__name__)

TAIL_S = 1.2  # picture after the last word: the takeaway lands, then the fade
TARGET_WORDS = 88  # Kokoro (af_heart, speed 1.0) measured ~3.4 words/s incl. pauses -> ~26 s
MIN_VOICE_S = 23.5  # less leaves the end of the 30 s film silent
MAX_SPEED = 1.15
HERE = Path(__file__).resolve().parent
SAFE_SOUND = """SFX, MUSIC, VOICE = True, True, "vo.wav"
m.room_bed([(0, .6), (info["dur"], .5)])
m.music(mood="warm")
m.voice(VOICE, at=.5)"""


@dataclass
class Tools:
    skill_dir: Path
    studio_dir: Path
    python: str  # Stickman Studio's virtualenv python (Playwright, Kokoro, numpy, scipy, Pillow)
    models_dir: str = ""
    voice_id: str = "af_heart"
    jobs: int = 0
    build_timeout_s: int = 1800

    def env(self) -> dict:
        e = {**os.environ, "PATH": f"{Path(self.python).parent}{os.pathsep}{os.environ.get('PATH', '')}"}
        if self.models_dir:
            e["STICKMAN_MODELS_DIR"] = self.models_dir
        return e

    def ffmpeg(self) -> str:
        """The ffmpeg the skill scripts use: next to the Stickman python, else on PATH."""
        local = Path(self.python).parent / "ffmpeg"
        return str(local) if local.exists() else (shutil.which("ffmpeg") or "ffmpeg")

    def run(self, args: list[str], cwd: Path | None = None, timeout: int = 600) -> subprocess.CompletedProcess:
        return subprocess.run(
            [self.python, *args], cwd=cwd, env=self.env(), capture_output=True, text=True, timeout=timeout
        )


@dataclass
class FilmResult:
    title: str
    script: dict
    sentences: list[dict]
    video: Path  # 1080x1920 for the lesson player
    landscape: Path  # raw film from the engine
    duration_s: float
    audit_issues: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    tokens: list[int] = field(default_factory=lambda: [0, 0])


class FilmError(Exception):
    pass


def make_film(
    topic: str,
    concept: str,
    workdir: Path,
    *,
    tools: Tools,
    llm: FilmLLM,
    objective: str = "",
    narration: list[str] | None = None,
    on_stage: Callable[[str], None] = lambda s: None,
    critique_rounds: int = 2,
) -> FilmResult:
    """A film for `concept`. With `narration` (a lesson's sentences) the script step is skipped and those words are
    spoken as written; the film is still FILM_S long, or just long enough if the narration cannot fit at MAX_SPEED."""
    workdir.mkdir(parents=True, exist_ok=True)
    notes: list[str] = []

    # 1-3. script + voice, fitted to FILM_S
    if narration:
        on_stage("voice")
        script = {"title": concept, "logline": concept, "set": "", "sentences": [x for x in narration if x.strip()]}
        voice, dur = _fit_given(tools, script["sentences"], workdir, notes)
    else:
        on_stage("script")
        script, voice = _fit_generated(tools, llm, topic, concept, objective, workdir, notes, on_stage)
        dur = FILM_S
    (workdir / "voice" / "timings.json").write_text(json.dumps(voice, indent=1))
    sentences = voice["sentences"]

    # 4. plan + code
    on_stage("plan")
    film = workdir / "film"
    if film.exists():
        shutil.rmtree(film)
    r = tools.run([str(tools.skill_dir / "scripts" / "new_film.py"), str(film)])
    if r.returncode != 0:
        raise FilmError(f"new_film failed: {r.stderr[-400:]}")
    shutil.copy(voice["voice"], film / "vo.wav")
    index = film / "index.html"  # portrait canvas: the engine reads its frame size from it
    index.write_text(
        index.read_text().replace("width=1920 height=1080", f"width={prompts.FRAME_W} height={prompts.FRAME_H}")
    )
    system = prompts.system_prompt(str(tools.skill_dir))
    plan = llm.json(
        system=system,
        prompt=prompts.film_prompt(script, sentences, dur, topic=topic, concept=concept),
        schema=prompts.FILM_SCHEMA,
    )
    (workdir / "plan.json").write_text(json.dumps({k: v for k, v in plan.items() if k != "film_js"}, indent=1))
    film_js = plan["film_js"]
    _write_sound(film, plan["sound_block"])

    # 5. must load and render
    on_stage("code")
    film_js, probe = _load_until_ok(tools, llm, system, film, film_js, dur, attempts=3)

    # 6. stills critique with vision
    on_stage("review")
    for rnd in range(critique_rounds):
        times = _still_times(probe["cuts"])
        sheet = workdir / f"stills_{rnd}.jpg"
        probe = _probe(tools, film, film_js, dur, times=times, sheet=sheet)
        if not probe["ok"]:
            film_js, probe = _load_until_ok(
                tools, llm, system, film, film_js, dur, attempts=2, first_errors=probe["errors"]
            )
            continue
        verdict = llm.json(
            system=system,
            prompt=prompts.critique_prompt(film_js, times, sentences),
            schema=prompts.FIX_SCHEMA,
            images=[sheet],
        )
        notes.append(f"review {rnd + 1}: {'ok' if verdict['ok'] else '; '.join(verdict['issues'][:4])}")
        if verdict["ok"] or not verdict["film_js"].strip():
            break
        film_js, probe = _load_until_ok(tools, llm, system, film, verdict["film_js"], dur, attempts=2)

    # 7. build + audit (+ one fix round)
    on_stage("render")
    out, issues = _build(tools, film)
    if issues:
        fix = llm.json(system=system, prompt=prompts.audit_fix_prompt(film_js, issues), schema=prompts.FIX_SCHEMA)
        if fix["film_js"].strip():
            candidate, p = _load_until_ok(tools, llm, system, film, fix["film_js"], dur, attempts=2, allow_fail=True)
            if p["ok"]:
                kept = film_js
                film_js = candidate
                shutil.rmtree(film / "shots", ignore_errors=True)
                out2, issues2 = _build(tools, film)
                notes.append(f"audit fix: {len(issues)} -> {len(issues2)} issues")
                if len(issues2) <= len(issues):
                    out, issues = out2, issues2
                else:  # the fix made it worse: rebuild the version that rendered
                    film_js = kept
                    _write_js(film, kept)
                    shutil.rmtree(film / "shots", ignore_errors=True)
                    out, issues = _build(tools, film)
            else:
                _write_js(film, film_js)  # keep the version that built

    # 8. captions + title for the 9:16 lesson player
    on_stage("compose")
    vertical = workdir / "film_9x16.mp4"
    compose_vertical(
        out,
        vertical,
        script["title"],
        picture_times(sentences),
        font_dir=tools.studio_dir / "assets" / "fonts",
        ffmpeg=tools.ffmpeg(),
    )
    return FilmResult(script["title"], script, sentences, vertical, out, float(dur), issues, notes, list(llm.usage))


def picture_times(sentences: list[dict]) -> list[dict]:
    """Sentence times on the picture's clock (the voice starts at VOICE_AT)."""
    return [
        {**s, "start": round(s["start"] + prompts.VOICE_AT, 3), "end": round(s["end"] + prompts.VOICE_AT, 3)}
        for s in sentences
    ]


def _fit_generated(tools, llm, topic, concept, objective, workdir, notes, on_stage) -> tuple[dict, dict]:
    """Write a script and speak it so the speech lands between MIN_VOICE_S and the FILM_S budget."""
    script = _script(llm, topic, concept, objective, TARGET_WORDS)
    on_stage("voice")
    voice = _narrate(tools, script["sentences"], workdir / "voice", speed=1.0)
    budget = FILM_S - prompts.VOICE_AT - TAIL_S
    if voice["duration"] > budget:
        speed = min(MAX_SPEED, voice["duration"] / budget + 0.01)
        notes.append(f"voice {voice['duration']:.1f}s > {budget:.1f}s; re-spoken at {speed:.2f}x")
        voice = _narrate(tools, script["sentences"], workdir / "voice", speed=speed)
        if voice["duration"] > budget:
            words = round(TARGET_WORDS * budget / voice["duration"] * speed) - 4
            notes.append(f"still {voice['duration']:.1f}s; asked for a {words}-word script")
            script = _script(llm, topic, concept, objective, words)
            voice = _narrate(tools, script["sentences"], workdir / "voice", speed=1.0)
            if voice["duration"] > budget:
                voice = _narrate(tools, script["sentences"], workdir / "voice", speed=MAX_SPEED)
            if voice["duration"] > budget:
                raise FilmError(f"narration does not fit {FILM_S}s ({voice['duration']:.1f}s)")
    if voice["duration"] < MIN_VOICE_S:
        words = round(len(" ".join(script["sentences"]).split()) * 26.0 / max(voice["duration"], 1))
        notes.append(f"voice {voice['duration']:.1f}s < {MIN_VOICE_S}s; asked for a {words}-word script")
        longer = _script(llm, topic, concept, objective, words)
        v2 = _narrate(tools, longer["sentences"], workdir / "voice", speed=1.0)
        if MIN_VOICE_S - 3 <= v2["duration"] <= budget or (v2["duration"] > budget and _fits_fast(v2, budget)):
            script = longer
            voice = (
                v2
                if v2["duration"] <= budget
                else _narrate(
                    tools, longer["sentences"], workdir / "voice", speed=min(MAX_SPEED, v2["duration"] / budget + 0.01)
                )
            )
        else:
            voice = _narrate(tools, script["sentences"], workdir / "voice", speed=1.0)
    return script, voice


def _fit_given(tools, sentences, workdir, notes) -> tuple[dict, int]:
    """Speak fixed narration (a lesson's words). Speed up to MAX_SPEED to fit FILM_S; if it still does not fit, the
    film gets longer instead of losing words (the lesson's quiz pauses depend on them)."""
    voice = _narrate(tools, sentences, workdir / "voice", speed=1.0)
    budget = FILM_S - prompts.VOICE_AT - TAIL_S
    if voice["duration"] > budget:
        speed = min(MAX_SPEED, voice["duration"] / budget + 0.01)
        voice = _narrate(tools, sentences, workdir / "voice", speed=speed)
        notes.append(f"lesson narration re-spoken at {speed:.2f}x")
        if voice["duration"] > budget and speed < MAX_SPEED:
            voice = _narrate(tools, sentences, workdir / "voice", speed=MAX_SPEED)
            notes.append(f"lesson narration re-spoken at {MAX_SPEED:.2f}x")
    dur = max(FILM_S, math.ceil(voice["duration"] + prompts.VOICE_AT + TAIL_S))
    if dur > FILM_S:
        notes.append(f"lesson narration is {voice['duration']:.1f}s; film extended to {dur}s")
    return voice, dur


# ---------------------------------------------------------------------------------------------------------------- steps


def _fits_fast(voice: dict, budget: float) -> bool:
    return voice["duration"] / MAX_SPEED <= budget


def _script(llm: FilmLLM, topic: str, concept: str, objective: str, words: int) -> dict:
    s = llm.json(prompt=prompts.script_prompt(topic, concept, objective, words), schema=prompts.SCRIPT_SCHEMA)
    s["sentences"] = [x.strip() for x in s["sentences"] if x.strip()]
    if len(s["sentences"]) < 3:
        raise FilmError("script has fewer than 3 sentences")
    s["title"] = s["title"].strip()[:60]
    return s


def _narrate(tools: Tools, sentences: list[str], out: Path, speed: float) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    params = out / "params.json"
    params.write_text(json.dumps({"sentences": sentences, "voice_id": tools.voice_id, "speed": round(speed, 3)}))
    r = tools.run(["boomdoom_tts.py", "--params", str(params), "--out", str(out)], cwd=tools.studio_dir, timeout=900)
    if r.returncode != 0:
        raise FilmError(f"narration failed: {r.stderr[-400:]}")
    return json.loads(r.stdout.strip().splitlines()[-1])


def _write_js(film: Path, film_js: str) -> None:
    (film / "film.js").write_text(film_js)


def _write_sound(film: Path, block: str) -> None:
    tpl = (film / "audio_mix.py").read_text()
    a = tpl.index("# ---- EDIT FROM HERE")
    a = tpl.index("\n", a) + 1
    b = tpl.index("# ---- TO HERE ----")
    (film / "audio_mix.py").write_text(tpl[:a] + block.strip() + "\n" + tpl[b:])


def _probe(
    tools: Tools, film: Path, film_js: str, dur: int, times: list[float] = (), sheet: Path | None = None
) -> dict:
    _write_js(film, film_js)
    args = [str(HERE / "probe.py"), str(film), str(tools.skill_dir / "scripts")]
    if sheet:
        args += ["--sheet", str(sheet), "--times", *[f"{t:.2f}" for t in times]]
    r = tools.run(args, timeout=300)
    if r.returncode != 0 or not r.stdout.strip():
        return {"ok": False, "errors": [f"probe crashed: {r.stderr[-400:]}"], "cuts": None, "dur": None}
    p = json.loads(r.stdout.strip().splitlines()[-1])
    if p["ok"]:
        cuts = p["cuts"]
        problems = []
        if p["dur"] != dur:
            problems.append(f"FILM.dur is {p['dur']}, it must be {dur}")
        if not cuts or cuts[0] != 0 or cuts[-1] != dur * prompts.FPS or any(b <= a for a, b in pairwise(cuts)):
            problems.append(f"FILM.cuts must start at 0, end at {dur * prompts.FPS} and increase: {cuts}")
        if problems:
            p.update(ok=False, errors=problems)
    return p


def _load_until_ok(tools, llm, system, film, film_js, dur, attempts, first_errors=None, allow_fail=False):
    errors = first_errors
    for _ in range(attempts + 1):
        if errors is None:
            probe = _probe(tools, film, film_js, dur, times=[0.5, dur / 2, dur - 1], sheet=film.parent / "load.jpg")
            if probe["ok"]:
                return film_js, probe
            errors = probe["errors"]
        log.info("film.js errors: %s", errors[:3])
        fix = llm.json(system=system, prompt=prompts.load_fix_prompt(film_js, errors), schema=prompts.FIX_SCHEMA)
        film_js, errors = fix["film_js"] or film_js, None
    probe = _probe(tools, film, film_js, dur, times=[0.5, dur / 2, dur - 1], sheet=film.parent / "load.jpg")
    if probe["ok"] or allow_fail:
        return film_js, probe
    raise FilmError(f"film.js still fails after fixes: {probe['errors'][:3]}")


def _still_times(cuts: list[int]) -> list[float]:
    mids = [round((a + b) / 2 / prompts.FPS, 2) for a, b in pairwise(cuts)]
    if len(mids) > 9:
        # Include the final shot so the stills review sees the payoff.
        mids = [mids[round(i * (len(mids) - 1) / 8)] for i in range(9)]
    return mids


def _build(tools: Tools, film: Path) -> tuple[Path, list[str]]:
    args = [str(tools.skill_dir / "scripts" / "build.py"), str(film), "--out", "film.mp4", "--crf", "20"]
    if tools.jobs:
        args += ["--jobs", str(tools.jobs)]
    r = tools.run(args, timeout=tools.build_timeout_s)
    if r.returncode != 0 and "audio_mix" in (r.stderr + r.stdout):
        log.warning("sound block failed, using the safe mix: %s", r.stderr[-300:])
        _write_sound(film, SAFE_SOUND)
        r = tools.run(args, timeout=tools.build_timeout_s)
    out = film / "film.mp4"
    if r.returncode != 0 or not out.exists():
        raise FilmError(f"build failed: {(r.stderr or r.stdout)[-600:]}")
    return out, _audit_issues(r.stdout)


def _audit_issues(stdout: str) -> list[str]:
    m = re.search(r"^ISSUES (\d+)\s*$", stdout, re.M)
    if not m or m.group(1) == "0":
        return []
    return [line.strip() for line in stdout[m.end() :].splitlines() if line.strip()]
