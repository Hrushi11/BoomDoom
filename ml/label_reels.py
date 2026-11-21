"""Teacher labels for collected reels (ml/data/reels) from an OpenAI vision model.

Each reel = captured frame (jpg) + Instagram caption text + sponsored flag (json), written by collect_reels.py.
Writes ml/data/reels/labels.jsonl (one line per reel; reruns skip labelled reels). Dev tooling only: frames of
the developer's own feed are sent to OpenAI; nothing here runs in the app.

    OPENAI_API_KEY=... python label_reels.py --model gpt-6.1-terra
"""

import argparse
import base64
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from reel_classifier import LABELS

HERE = Path(__file__).resolve().parent
CLASSES = [*LABELS, "other"]

GUIDE = {
    "comedy": "humour of any kind: relatable memes (text over a video), POV skits, jokes, funny clips, sarcasm",
    "fitness": "exercise, gym, sports training, cycling/running as a workout, physique",
    "education": "teaches something: explainers, how-tos, science, history, language, tech tips",
    "food": "cooking, recipes, restaurants, eating, drinks such as coffee as the subject",
    "news": "current events, politics, journalism, public figures speaking at events",
    "finance": "money, investing, stocks, saving, business numbers, crypto",
    "rage_bait": "content designed to provoke anger or outrage, inflammatory takes, heated fights",
    "ads": "a product or service being promoted (sponsored post, brand promo, 'link in bio' selling)",
    "other": "none of the above clearly applies (pets with no joke, music, dance, travel, vlogs, aesthetics...)",
}

SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["label", "confidence", "secondary", "suggested_category", "reason"],
    "properties": {
        "label": {"type": "string", "enum": CLASSES},
        "confidence": {"type": "number"},
        "secondary": {"type": "array", "items": {"type": "string", "enum": CLASSES}},
        "suggested_category": {"type": "string"},
        "reason": {"type": "string"},
    },
}

PROMPT = """You label Instagram Reels for a screen-time app. You see one screenshot of the Reels screen (Instagram's
buttons and username are visible; read any text burned into the video) and the caption text Instagram exposed.

Pick the single best label:
{guide}

Rules: judge what a viewer would say the reel is about. A meme whose purpose is a joke is comedy even if its topic
is work or relationships. Use "ads" only for promotion. If two fit, put the weaker in "secondary".
confidence is 0..1. suggested_category: 1-3 words naming the reel's genre in your own words (even if it fits a
label), so we can find missing categories. reason: one short sentence.

Caption text: {caption}
Instagram marked it sponsored: {sponsored}"""


def label_one(client, model, jpg):
    meta = json.loads(jpg.with_suffix(".json").read_text())
    caption = (meta.get("caption_text") or "").removeprefix("[sponsored] ") or "(none)"
    image = base64.b64encode(jpg.read_bytes()).decode()
    guide = "\n".join(f"- {k}: {v}" for k, v in GUIDE.items())
    resp = client.chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": [
            {"type": "text", "text": PROMPT.format(guide=guide, caption=caption, sponsored=bool(meta.get("sponsored")))},
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image}", "detail": "low"}},
        ]}],
        response_format={"type": "json_schema", "json_schema": {"name": "reel_label", "schema": SCHEMA, "strict": True}},
    )
    out = json.loads(resp.choices[0].message.content)
    usage = resp.usage
    return {"id": jpg.stem, "caption": caption, "sponsored": bool(meta.get("sponsored")),
            "phone_label": meta.get("label"), **out,
            "tokens": [usage.prompt_tokens, usage.completion_tokens] if usage else None}


def main():
    from openai import OpenAI

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=HERE / "data/reels")
    parser.add_argument("--model", default="gpt-6.1-terra")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()
    out = args.data / "labels.jsonl"
    done = {json.loads(line)["id"] for line in out.open()} if out.exists() else set()
    todo = [j for j in sorted(args.data.glob("*.jpg")) if j.stem not in done and j.with_suffix(".json").exists()]
    if args.limit:
        todo = todo[: args.limit]
    client = OpenAI()
    tokens = [0, 0]
    with ThreadPoolExecutor(args.workers) as pool, out.open("a") as f:
        for jpg, result in zip(todo, pool.map(lambda j: _safe(label_one, client, args.model, j), todo)):
            if "error" in result:
                print(f"{jpg.stem}: {result['error']}")
                continue
            f.write(json.dumps(result, ensure_ascii=False) + "\n")
            if result["tokens"]:
                tokens = [tokens[0] + result["tokens"][0], tokens[1] + result["tokens"][1]]
    print(f"labelled {len(todo)} reels; tokens in/out {tokens[0]:,}/{tokens[1]:,}")


def _safe(fn, *args):
    try:
        return fn(*args)
    except Exception as e:  # keep going; failed reels are retried on the next run
        return {"error": f"{type(e).__name__}: {e}"[:300]}


if __name__ == "__main__":
    main()
