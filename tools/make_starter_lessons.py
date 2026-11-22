"""Generate the bundled starter lessons (US-06A, Tech Spec §7.3): one per preset topic.

Writes android/app/src/main/assets/lessons/{index.json, <id>.json, <id>.mp4}.
Videos are silent placeholders (cream paper + the topic's stickman art + timed captions) until real
Stickman Studio renders replace them; manifests follow the backend schema, so the player treats them
exactly like downloaded lessons.

    backend/.venv/bin/python tools/make_starter_lessons.py   # needs Pillow + imageio-ffmpeg (or ffmpeg on PATH)
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "android/app/src/main/assets/lessons"
ART = ROOT / "android/core/designsystem/src/main/res/drawable-nodpi"
FONT = ROOT / "android/core/designsystem/src/main/res/font/plus_jakarta_sans.ttf"
WPS = 3.0  # narration words per second (Tech Spec §5.5 / Product Spec §7.4)

LESSONS = [
    dict(topic="psychology", name="Psychology", concept="The Zeigarnik effect", format="guess_first",
         segments=[("hook", "Ever noticed you can't stop thinking about an unfinished task?"),
                   ("setup", "In the 1920s, a psychologist noticed waiters remembered unpaid orders perfectly, but forgot them once they were paid."),
                   ("reveal", "Your brain keeps unfinished tasks active, like an open tab. Finishing a task lets it close the tab."),
                   ("takeaway", "So to beat procrastination, just start. Two minutes of work keeps the task pulling you back.")],
         interaction=("setup", "predict", "Why did the waiters forget paid orders?",
                      ["Paid orders were boring", "The brain lets go of finished tasks", "They were tired"], 1,
                      "Exactly! Finished tasks stop nagging the brain.", "Close. It's about the task being finished."),
         recap="Unfinished tasks stick in your mind. Start for 2 minutes to beat procrastination.", disclaimer=None),
    dict(topic="interview", name="Interview Prep", concept="The STAR method", format="explainer",
         segments=[("hook", "Tell me about a time you solved a hard problem. Freezing already?"),
                   ("setup", "Interviewers want a story, not a list. STAR gives you one: Situation, Task, Action, Result."),
                   ("reveal", "Spend most of your time on Action, what you did step by step. End with a Result, ideally with a number."),
                   ("takeaway", "Prepare three STAR stories before any interview and adapt them to each question.")],
         interaction=("setup", "mcq", "Which STAR part deserves the most time?", ["Situation", "Task", "Action", "Result"], 2,
                      "Yes! Your actions show how you think.", "Not quite. Interviewers care most about what you did."),
         recap="Answer behavioural questions with STAR, and spend most of your time on the Action.", disclaimer=None),
    dict(topic="gk", name="General Knowledge", concept="Most time zones", format="guess_first",
         segments=[("hook", "Which country has the most time zones? It's not the one you think."),
                   ("setup", "Russia is the biggest country on Earth, and it spans eleven time zones."),
                   ("reveal", "But France has twelve, thanks to overseas territories from the Caribbean to the Pacific."),
                   ("takeaway", "Islands and territories can stretch a country across the globe, far beyond its mainland.")],
         interaction=("setup", "true_false", "Russia has the most time zones in the world.", ["True", "False"], 1,
                      "Right! France has twelve.", "Surprise: France beats Russia, with twelve."),
         recap="France has the most time zones: twelve, thanks to its overseas territories.", disclaimer=None),
    dict(topic="finance", name="Personal Finance", concept="The rule of 72", format="explainer",
         segments=[("hook", "Want to know how fast your money could double? Just divide."),
                   ("setup", "The rule of 72: divide 72 by the yearly return to estimate the years it takes to double."),
                   ("reveal", "At 8 percent a year, 72 divided by 8 is 9. So your money doubles in about nine years."),
                   ("takeaway", "It works for inflation too: at 6 percent inflation, prices double in about twelve years.")],
         interaction=("setup", "mcq", "At 12% a year, about how long until money doubles?", ["3 years", "6 years", "12 years"], 1,
                      "Spot on: 72 ÷ 12 = 6.", "Try it: 72 ÷ 12 = 6 years."),
         recap="Divide 72 by the yearly rate to estimate how many years it takes to double.",
         disclaimer="General education only, not financial advice."),
    dict(topic="science", name="Science", concept="Why the sky is blue", format="explainer",
         segments=[("hook", "Sunlight is white. So why is the sky blue?"),
                   ("setup", "Sunlight holds every colour. Passing through air, tiny gas molecules scatter it around."),
                   ("reveal", "Short blue waves scatter far more than long red ones, so blue reaches your eyes from every direction."),
                   ("takeaway", "At sunset, light crosses more air, the blue gets scattered away, and reds and oranges remain.")],
         interaction=("setup", "predict", "Which colour scatters the most in air?", ["Red", "Blue", "Green"], 1,
                      "Yes! Short blue waves scatter the most.", "It's blue: shorter waves scatter more."),
         recap="Air scatters short blue light the most, so the sky looks blue.", disclaimer=None),
    dict(topic="history", name="History", concept="The Great Wall from space", format="myth_fact",
         segments=[("hook", "You've heard the Great Wall of China is visible from space. Is it?"),
                   ("setup", "The wall is incredibly long, but mostly just a few metres wide, about as wide as a road."),
                   ("reveal", "Astronauts in low orbit say it's almost impossible to spot with the naked eye. It blends into the land."),
                   ("takeaway", "Long is not the same as visible. Width and colour contrast matter far more.")],
         interaction=("setup", "true_false", "You can easily see the Great Wall from space with your eyes.", ["True", "False"], 1,
                      "Correct, that's a myth!", "It's a myth: the wall is too narrow."),
         recap="The Great Wall isn't easily visible from space: it's long but narrow.", disclaimer=None),
    dict(topic="communication", name="Communication", concept="The power of a pause", format="explainer",
         segments=[("hook", "Want to sound more confident? Try saying less, more slowly."),
                   ("setup", "When we're nervous, we fill silence with um, like and you know. Listeners hear uncertainty."),
                   ("reveal", "Swap filler words for a short pause. One calm second sounds confident and buys time to think."),
                   ("takeaway", "In your next conversation, catch one filler word and replace it with a pause.")],
         interaction=("setup", "mcq", "What should replace filler words?", ["Speaking faster", "A short pause", "A joke"], 1,
                      "Yes! Pauses sound calm and confident.", "Try a short pause instead."),
         recap="Swap filler words for short pauses to sound calmer and more confident.", disclaimer=None),
    dict(topic="tech", name="Tech Explained", concept="What the padlock means", format="myth_fact",
         segments=[("hook", "That little padlock in your browser. What does it actually do?"),
                   ("setup", "It means the site uses HTTPS. Your browser and the site agree on a secret key nobody else knows."),
                   ("reveal", "Everything you send is encrypted with that key, so others on the same Wi-Fi only see scrambled data."),
                   ("takeaway", "But padlock means private, not trustworthy. A scam site can have a padlock too.")],
         interaction=("setup", "true_false", "A padlock means the website is safe and honest.", ["True", "False"], 1,
                      "Right! It only means the connection is encrypted.", "Careful: scam sites can have padlocks too."),
         recap="HTTPS encrypts your connection, but a padlock doesn't prove a site is honest.", disclaimer=None),
]


def ffmpeg() -> str:
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return shutil.which("ffmpeg") or sys.exit("ffmpeg not found")


def chunks(words: list[str], size: int = 5) -> list[list[str]]:
    return [words[i:i + size] for i in range(0, len(words), size)]


def build(lesson: dict, tmp: Path) -> dict:
    from PIL import Image, ImageDraw

    lid = f"starter-{lesson['topic']}"
    # timeline: captions of ~5 words, 3 words/s; segment ends at its last word
    t, seg_ends, captions = 0.6, {}, []
    for seg_id, text in lesson["segments"]:
        for c in chunks(text.split()):
            d = len(c) / WPS
            captions.append((t, t + d, " ".join(c)))
            t += d
        seg_ends[seg_id] = round(t, 2)
        t += 0.3
    duration = round(t + 1.2, 2)

    # background: cream paper + topic art
    bg = Image.new("RGB", (720, 1280), (250, 246, 238))
    art = Image.open(ART / f"illus_topic_{lesson['topic']}.webp").convert("RGB")
    art = art.resize((640, round(art.height * 640 / art.width)))
    bg.paste(art, (40, 300))
    ImageDraw.Draw(bg).rounded_rectangle((40, 300, 680, 300 + art.height), radius=28, outline=(233, 226, 212), width=3)
    bg_path = tmp / f"{lid}.png"
    bg.save(bg_path)

    filters = []
    for i, (a, b, text) in enumerate(captions):
        tf = tmp / f"{lid}-{i}.txt"
        tf.write_text(text)
        filters.append(
            f"drawtext=fontfile='{FONT}':textfile='{tf}':fontcolor=0x14151A:fontsize=46:"
            f"x=(w-text_w)/2:y=980:box=1:boxcolor=0xFFE3A8@0.95:boxborderw=18:enable='between(t,{a:.2f},{b:.2f})'"
        )
    out = OUT / f"{lid}.mp4"
    cmd = [ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", "-loop", "1", "-framerate", "24", "-i", str(bg_path),
           "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono", "-t", f"{duration}", "-vf", ",".join(filters),
           "-c:v", "libx264", "-preset", "slow", "-crf", "30", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "32k",
           "-shortest", "-movflags", "+faststart", str(out)]
    subprocess.run(cmd, check=True)

    after, itype, q, opts, ans, ok, bad = lesson["interaction"]
    return {
        "lesson_id": lid, "topic": {"id": lesson["topic"], "name": lesson["name"]}, "concept": lesson["concept"],
        "format": lesson["format"], "level": "beginner", "language": "en", "duration_s": duration,
        "segments": [{"id": s, "end_s": seg_ends[s]} for s, _ in lesson["segments"]],
        "interactions": [{"id": "i1", "after_segment": after, "pause_at_s": round(seg_ends[after] + 0.15, 2), "type": itype,
                          "question": q, "options": opts, "answer_index": ans, "feedback_correct": ok, "feedback_wrong": bad}],
        "recap": lesson["recap"], "go_deeper": [], "disclaimer": lesson["disclaimer"], "version": 1,
    }


def build_film(lesson: dict, work: Path) -> dict:
    """Same narration and quiz, rendered by the OpenAI-directed film pipeline (backend/workers/film_worker)."""
    import sys

    sys.path.insert(0, str(ROOT / "backend"))
    from workers.film_worker.director import Tools, make_film
    from workers.film_worker.llm import FilmLLM
    from workers.render_worker.pause_times import caption_words, segment_end_times
    from workers.render_worker.renderers import film_project, split_sentences

    lid = f"starter-{lesson['topic']}"
    segments = [{"id": s, "narration": text} for s, text in lesson["segments"]]
    studio = os.environ.get("STICKMAN_STUDIO_CHECKOUT", str(ROOT.parent / "Stickman-Studio"))
    tools = Tools(skill_dir=ROOT / "vendor/stickman-skill", studio_dir=ROOT / "vendor/stickman-studio",
                  python=f"{studio}/.venv/bin/python", models_dir=f"{studio}/models")
    result = make_film(lesson["name"], lesson["concept"], work / lid, tools=tools, llm=FilmLLM(record_spend=False),
                       narration=split_sentences(segments), on_stage=lambda st: print(f"  {lid}: {st}", flush=True))
    project = film_project(result)
    seg_ends, _ = segment_end_times(segments, caption_words(project), result.duration_s)
    out = OUT / f"{lid}.mp4"
    subprocess.run([str(Path(tools.ffmpeg())), "-hide_banner", "-loglevel", "error", "-y", "-i", str(result.video),
                    "-vf", "scale=720:1280", "-c:v", "libx264", "-preset", "slow", "-crf", "28", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "64k", "-movflags", "+faststart", str(out)], check=True)
    after, itype, q, opts, ans, ok, bad = lesson["interaction"]
    return {
        "lesson_id": lid, "topic": {"id": lesson["topic"], "name": lesson["name"]}, "concept": lesson["concept"],
        "format": lesson["format"], "level": "beginner", "language": "en", "duration_s": result.duration_s,
        "segments": [{"id": s, "end_s": round(seg_ends[s], 2)} for s, _ in lesson["segments"]],
        "interactions": [{"id": "i1", "after_segment": after, "pause_at_s": round(seg_ends[after] + 0.15, 2), "type": itype,
                          "question": q, "options": opts, "answer_index": ans, "feedback_correct": ok, "feedback_wrong": bad}],
        "recap": lesson["recap"], "go_deeper": [], "disclaimer": lesson["disclaimer"], "version": 2,
    }


def main() -> None:
    """python tools/make_starter_lessons.py [--film] [--only finance tech ...] [--parallel 2]

    --film renders with the film pipeline (needs OPENAI_API_KEY, the Stickman Studio checkout with Playwright and
    Chromium in its virtualenv; ~8 min and ~$0.50 per lesson). Without it, the old placeholder slides are made."""
    import argparse
    from concurrent.futures import ThreadPoolExecutor

    ap = argparse.ArgumentParser()
    ap.add_argument("--film", action="store_true")
    ap.add_argument("--only", nargs="*", default=[])
    ap.add_argument("--parallel", type=int, default=2)
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    chosen = [x for x in LESSONS if not a.only or x["topic"] in a.only]
    with tempfile.TemporaryDirectory() as d:
        if a.film:
            work = Path(os.environ.get("STARTER_FILM_WORKDIR", d))
            with ThreadPoolExecutor(max(1, a.parallel)) as pool:
                manifests = list(pool.map(lambda x: build_film(x, work), chosen))
        else:
            manifests = [build(x, Path(d)) for x in chosen]
    for m in manifests:
        (OUT / f"{m['lesson_id']}.json").write_text(json.dumps(m, indent=2, ensure_ascii=False))
        print(f"{m['lesson_id']}: {m['duration_s']}s")
    index = [{"manifest": f"lessons/{x['lesson_id']}.json", "video": f"lessons/{x['lesson_id']}.mp4"} for x in
             [{"lesson_id": f"starter-{y['topic']}"} for y in LESSONS]]
    (OUT / "index.json").write_text(json.dumps(index, indent=2))


if __name__ == "__main__":
    main()
