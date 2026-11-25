"""Walk the BoomDoom learner journey over the public API, exactly as the Android app does.

    .venv/bin/python scripts/demo_journey.py [--api http://localhost:8000] [--topic "System Design"] [--wait 600]

1. Register a new device account                      POST /v1/users
2. Pick topics (onboarding)                           PUT  /v1/users/{id}/lesson-profile
3. Wait while lessons are generated                   GET  /v1/topics   (preparing -> ready)
4. Open the Learn feed                                GET  /v1/feed     (video URL, quiz pauses, recap)
5. Watch a lesson: start, answer the quiz, complete   POST /v1/events
6. See the feed move on (the watched lesson is gone; stock is refilled in the background)

It prints the account's user id and tokens at the end, so you can keep using it in Swagger (/docs → Authorize).
Works against a local stack (scripts/run_local.py, including --mock) or the deployed POC.
"""

from __future__ import annotations

import argparse
import sys
import time
import uuid
from datetime import UTC, datetime

import httpx


def step(n: int, text: str) -> None:
    print(f"\n\033[1m{n}. {text}\033[0m", flush=True)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--api", default="http://localhost:8000")
    ap.add_argument(
        "--topic", action="append", help="topic name (repeatable). Default: System Design, Personal Finance"
    )
    ap.add_argument("--level", default="beginner", choices=["beginner", "intermediate", "advanced"])
    ap.add_argument("--wait", type=int, default=600, help="seconds to wait for the first ready lesson")
    a = ap.parse_args()
    topics = a.topic or ["System Design", "Personal Finance"]
    c = httpx.Client(base_url=a.api.rstrip("/"), timeout=30)

    step(1, "Register a device account (the app does this silently on first launch)")
    r = c.post(
        "/v1/users", json={"install_secret_hash": uuid.uuid4().hex * 2, "app_version": "demo", "locale": "en-IN"}
    )
    r.raise_for_status()
    tok = r.json()
    uid, h = tok["user_id"], {"Authorization": f"Bearer {tok['access_token']}"}
    print(f"   user_id {uid}")

    step(2, f"Pick topics: {', '.join(topics)} ({a.level})")
    r = c.put(
        f"/v1/users/{uid}/lesson-profile",
        headers=h,
        json={
            "language": "en",
            "voice_id": "af_heart",
            "length_s": 30,
            "topics": [{"name": t, "level": a.level, "type": "custom"} for t in topics],
        },
    )
    r.raise_for_status()
    for t in r.json()["topics"]:
        print(f"   topic {t['topic_id']}  {t['status']}")

    step(3, "Wait for lessons (syllabus -> scripts -> renders). Watch it live at /trackVideoGen")
    deadline, last = time.time() + a.wait, ""
    while True:
        ts = c.get("/v1/topics", headers=h).json()
        line = "   " + "   ".join(f"{t['name']}: {t['status']} ({t['ready_count']} ready)" for t in ts)
        if line != last:
            print(line, flush=True)
            last = line
        if any(t["ready_count"] > 0 for t in ts):
            break
        if any(t["status"] == "failed" for t in ts) or time.time() > deadline:
            print("   no lesson ready in time; check the worker logs / tracker", file=sys.stderr)
            return 1
        time.sleep(5)

    step(4, "Open the Learn feed")
    feed = c.get("/v1/feed", headers=h).json()
    for item in feed:
        print(f"   - {item.get('concept')}  [{item.get('topic', {}).get('name', '')}]  {item.get('duration_s')} s")
    lesson = feed[0]
    print(f"\n   First lesson: {lesson.get('concept')}")
    print(f"   video: {lesson.get('video_url')}")
    for p in lesson.get("pauses", []) or lesson.get("interactions", []):
        q = p.get("question") or p.get("interaction", {}).get("question")
        print(f"   quiz at {p.get('at_s', p.get('pause_at_s', '?'))} s: {q}")
    print(f"   recap: {lesson.get('recap')}")

    step(5, "Watch it: start, answer the first question, complete")
    now = lambda: datetime.now(UTC).isoformat()  # noqa: E731
    first_q = (lesson.get("interactions") or [{}])[0]
    events = [{"event_id": str(uuid.uuid4()), "type": "lesson_started", "lesson_id": lesson["lesson_id"], "at": now()}]
    if first_q.get("id"):
        events.append(
            {
                "event_id": str(uuid.uuid4()),
                "type": "interaction_answered",
                "lesson_id": lesson["lesson_id"],
                "at": now(),
                "data": {"interaction_id": first_q["id"], "answer": first_q.get("answer_index") or 0, "correct": True},
            }
        )
    events.append(
        {"event_id": str(uuid.uuid4()), "type": "lesson_completed", "lesson_id": lesson["lesson_id"], "at": now()}
    )
    r = c.post("/v1/events", headers=h, json={"events": events})
    r.raise_for_status()
    print(f"   accepted {r.json()['accepted']} events")

    step(6, "The feed moves on")
    after = c.get("/v1/feed", headers=h).json()
    gone = lesson["lesson_id"] not in {x["lesson_id"] for x in after}
    print(f"   feed now has {len(after)} lesson(s); the completed one is gone: {gone}")

    print("\n\033[1mDone.\033[0m Keep exploring as this user in Swagger (/docs -> Authorize -> Bearer token):")
    print(f"   user_id       {uid}\n   access_token  {tok['access_token']}\n   refresh_token {tok['refresh_token']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
