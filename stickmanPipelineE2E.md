# Stickman lesson pipeline, end to end

How a topic picked in the app becomes a 30-second stickman film with quiz pauses. For the design of the film
director itself (prompts, engine patch, how quality was tuned), see [stickManCustom.md](stickManCustom.md).

```
App picks topic ──► SYLLABUS ──► SCRIPT ──► RENDER (FilmRenderer) ──► S3 + manifest ──► App plays it
                    (script-worker)          voice → plan → code → review → render → compose
                                             (render-worker × 3)
```

## 1. Topic to lesson plan (script worker, OpenAI)

- When a topic is added in the app, the backend creates a **syllabus**: an ordered list of concepts, such as
  "1. Peak Ingress Sizing" and "5. Hot-Key Mitigation" for "streaming hld".
- Each topic keeps **5 lessons in stock**. The refill sweep (every 10 min) tops it up as lessons are watched.
- For each lesson, a **script job** writes the lesson:
  - the narration, split into 3 segments of about 88 words in total (roughly 30 s spoken)
  - the quiz questions, and after which segment each one appears
  - the recap

  It then queues a **render job**.

## 2. Render: the film director (render workers, 3 in parallel)

Code: `backend/workers/render_worker/renderers.py` (`FilmRenderer`) and
`backend/workers/film_worker/director.py` (`make_film`). Each film goes through the steps shown on the tracker's
progress bar:

| Step | What happens |
|---|---|
| **voice** | The narration is spoken **sentence by sentence with Kokoro** (the Stickman Studio voice, `af_heart`). If it runs over 30 s, it's re-spoken up to 1.15× faster. If it still doesn't fit, the film is made slightly longer rather than cutting words. Every word gets a timestamp. |
| **plan** | **GPT-6.2** (`gpt-6.2-sol`) is given the engine's rules (vendored `vendor/stickman-skill`), the sentences with their timings, and a portrait 1080×1920 canvas. It returns a scene plan (beats, setting, shots), the film as JavaScript (`film.js`), and a sound mix. |
| **code** | `film.js` is loaded in headless Chromium to check it runs. Errors go back to GPT, with up to 3 attempts. |
| **review** | Still frames at each scene cut are put together as a contact sheet. GPT's vision checks them against the narration for overlaps, figures off-screen and unreadable text, and returns a corrected `film.js` if needed. `FILM_REVIEW_ROUNDS` sets the number of rounds (default 1). |
| **render** | `build.py` renders all **900 frames** (30 fps × 30 s) in headless Chromium (`FILM_RENDER_JOBS` browser processes per worker, 2 on the server). It mixes the voice with the sound effects, then **audits** the result (blank frames, clipping, timing). If the audit finds problems, GPT gets one chance to fix them, and the better of the two versions is kept. |
| **compose** | ffmpeg adds **captions**, highlighted from the word timings, and the title, giving the final vertical MP4 (`film_worker/compose.py`). |

Between steps, the worker records the current step (`job.progress`) and checks for an operator cancel, so the
tracker's **Stop** button takes effect at the next step.

## 3. Publish (render pipeline)

Code: `backend/workers/render_worker/pipeline.py`.

- **Quiz pause times** come from the word timings: each question is placed at the exact end of the segment it
  follows.
- The video is compressed to 720p and a thumbnail is made.
- Everything is uploaded to **S3** under `lessons/<id>/`:
  - `lesson_720p.mp4`, the lesson video
  - the thumbnail
  - `manifest.json`: video, pause times, questions, recap
  - `project.json`: the film's notes, review findings, word timings and token use
- The lesson is marked **ready**, and the topic changes from "Preparing" to "Ready".
- Identical lessons are **shared between users**: the same topic, concept and engine reuse one video instead of
  rendering again (`share_key`, including `lesson_mix_version()`).

## 4. In the app

- The app **syncs** the feed, gets signed S3 links and **downloads** videos for offline playback.
- The player **pauses at the quiz points**, then shows the recap and saves it to "Learned".
- The 8 bundled starter lessons (`android/app/src/main/assets/lessons/`) were made with this same pipeline
  (`tools/make_starter_lessons.py --film`), so they play offline from day one.

## Operating it

- **Tracker:** `https://<server>/trackVideoGen`. Sign in with `admin_password` from `infra/poc/terraform.tfvars`.
  It shows films rendering now (with their current step), the queue in pick-up order, and the full history with
  S3 links. It also has Prioritize, Cancel and Stop buttons.
- **API:** the same controls are under `/v1/admin/*` (see `deploy/poc/README.md`), listed in Swagger at `/docs`.
- **Engine switch:** `RENDERER=film` (default), `stickman` (the old renderer) or `placeholder`. It's the
  Terraform variable `renderer`.
- **Speed and cost settings:**
  - `FILM_REVIEW_ROUNDS`
  - `FILM_RENDER_JOBS`
  - `OPENAI_DAILY_COST_CAP_USD`: Terraform variable `openai_daily_cost_cap_usd`, currently 100. When the cap is
    reached, jobs retry later rather than fail.
  - the number of render workers: `deploy: replicas` in `deploy/poc/docker-compose.yml`, which deploys with a push
- **Deploys restart renders.** A push restarts the workers, and films in progress start over from the beginning
  (and pay for their OpenAI calls again).

## Cost and time (observed 2026-11-22)

- About **15–25 minutes per film** with 3 rendering at once on a c7i.xlarge (4 vCPU), against about 9 minutes when
  films rendered one at a time.
- About **$0.5–1 of OpenAI per film**, mostly from the plan, review and fix calls.
