"""Generate the film art described in designs/film-assets/PROMPTS.md with the OpenAI image API.

    python tools/gen_film_assets.py anchor              # the style anchor only
    python tools/gen_film_assets.py all [--force]       # everything else, using the anchor as a style reference
    python tools/gen_film_assets.py bg_meeting_room prop_laptop ...   # named files only

Reads OPENAI_API_KEY from the environment. Existing files are skipped unless --force.
"""

from __future__ import annotations

import base64
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from openai import OpenAI

DIR = Path(__file__).resolve().parents[1] / "designs" / "film-assets"
MODEL = "gpt-image-2"
PROP_MODEL = "gpt-image-1.5"  # gpt-image-2 cannot make transparent backgrounds
WORKERS = 16  # images generated at once


def _quote(text: str) -> str:
    return " ".join(line.lstrip("> ").strip() for line in text.strip().splitlines() if line.startswith(">"))


def load_jobs() -> list[dict]:
    md = (DIR / "PROMPTS.md").read_text()
    sec = {k: md[md.index(a) : md.index(b) if b else len(md)] for k, a, b in [
        ("anchor", "## 0.", "## 1."), ("bg", "## 1.", "## 2."), ("char", "## 2.", "## 3."), ("prop", "## 3.", "## 4."),
    ]}
    jobs = [{"file": "style_anchor.png", "prompt": _quote(sec["anchor"].split("Size 1024×1536 (portrait).")[1]),
             "size": "1024x1536", "kind": "anchor"}]

    def rules(s):  # the first quote block after "Rules for every"
        block = s.split("Rules for every", 1)[1].split("\n\n", 2)[1]
        return _quote(block)

    bg_rules = rules(sec["bg"])
    for part in sec["bg"].split("### ")[1:]:
        file = re.search(r"File: `([^`]+)`", part).group(1)
        jobs.append({"file": file, "prompt": _quote(part.split("`\n", 1)[1]) + " " + bg_rules,
                     "size": "1024x1536", "kind": "bg"})
    for kind, size in (("char", "1536x1024"), ("prop", "1024x1024")):
        r = rules(sec[kind])
        for file, desc in re.findall(r"^\| `([a-z_]+\.png)` \| (.+?) \|$", sec[kind], re.M):
            lead = "Character design sheet: " if kind == "char" else "Draw this object: "
            jobs.append({"file": file, "prompt": lead + desc + " " + r, "size": size, "kind": kind})
    return jobs


def generate(client: OpenAI, job: dict, anchor: Path | None) -> str:
    out = DIR / job["file"]
    if out.exists() and not job.get("force"):  # made by another run meanwhile
        return f"{job['file']} skipped"
    kw = {"model": MODEL, "prompt": job["prompt"], "size": job["size"], "quality": "high", "n": 1}
    if job["kind"] == "prop":
        kw["background"], kw["model"] = "transparent", PROP_MODEL
    if anchor and job["kind"] != "anchor":
        kw["prompt"] = "Match the visual style and colour palette of the attached reference image exactly. " + kw["prompt"]
        with anchor.open("rb") as f:
            res = client.images.edit(image=[f], **kw)
    else:
        res = client.images.generate(**kw)
    out.write_bytes(base64.b64decode(res.data[0].b64_json))
    return f"{job['file']} ok"


def main(argv: list[str]) -> None:
    force = "--force" in argv
    names = [a for a in argv if not a.startswith("--")] or ["anchor"]
    jobs = load_jobs()
    if names == ["anchor"]:
        todo = [j for j in jobs if j["kind"] == "anchor"]
    elif names == ["all"]:
        todo = [j for j in jobs if j["kind"] != "anchor"]
    else:
        todo = [j for j in jobs if j["file"].removesuffix(".png") in names]
    todo = [j for j in todo if force or not (DIR / j["file"]).exists()]
    anchor = DIR / "style_anchor.png"
    anchor = anchor if anchor.exists() else None
    print(f"{len(todo)} to generate with {MODEL}", flush=True)
    client = OpenAI(timeout=300, max_retries=4)  # retries back off on rate limits
    for j in todo:
        j["force"] = force
    with ThreadPoolExecutor(WORKERS) as pool:
        futures = {pool.submit(generate, client, j, anchor): j for j in todo}
        from concurrent.futures import as_completed

        for fut in as_completed(futures):
            j = futures[fut]
            try:
                print(fut.result(), flush=True)
            except Exception as e:  # keep going; rerun picks up what is missing
                print(f"{j['file']} FAILED: {str(e)[:300]}", flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
