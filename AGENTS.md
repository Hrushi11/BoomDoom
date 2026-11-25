# BoomDoom: setup, run and review guide

**Turn doom into boom.** BoomDoom is an Android app that watches how you scroll Instagram Reels (on the phone only) and
swaps some of that time for 30-second stickman lessons on topics you chose. A backend writes each lesson with GPT and
animates it as a stickman film with voice, captions and quiz pauses.

**Demo video:** [`AppDemo.mp4`](AppDemo.mp4) (3.5 min, the app end to end) · **Slides:**
[`BoomDoom Turn doom into boom.pdf`](<BoomDoom Turn doom into boom.pdf>) (5 slides: problem, product, how it works, next)

## Live deployment (try this first)

| What | URL | Login |
|---|---|---|
| API + Swagger | https://100-22-80-77.sslip.io/docs | none; create a learner (see "Test logins") |
| Video generation tracker | https://100-22-80-77.sslip.io/trackVideoGen | password shared with the organisers on request; locally it's `boomdoom-admin` |
| Android app (APK, 99 MB) | https://github.com/Hrushi11/BoomDoom/releases/download/v0.2.1/BoomDoom-v0.2.1.apk | none; the app signs in silently |
| Health | https://100-22-80-77.sslip.io/healthz | |

The live server makes **real** lessons: GPT writes them and real stickman films are rendered. A new topic's first
lesson takes about **10–20 minutes**. Lessons that already exist for the same topic and concept are shared at once.

---

## 1. What you can review, and how

| Touch point | Who uses it | How to reach it | Section |
|---|---|---|---|
| **Android app** (main product) | Learner | Emulator or phone with the APK; works on the live backend or yours | [Journey C](#journey-c-the-android-app) |
| **Learner API** (everything the app does) | Learner, via the app | Swagger at `/docs`, `scripts/demo_journey.py`, curl | [Journey A](#journey-a-learner-over-the-api-what-the-app-does) |
| **Video generation tracker** | Operator | `/trackVideoGen` in a browser | [Journey B](#journey-b-operator-video-generation-tracker) |
| **Admin queue API** | Operator, via scripts | `/v1/admin/*` with a session cookie or `X-Admin-Token` | [Journey B](#journey-b-operator-video-generation-tracker) |

The backend runs fully on a fresh Linux or macOS machine with **no API keys** (mock mode). The Android app needs an
emulator or phone.

---

## Codebase map (where the core things live)

```
BoomDoom/
├── AGENTS.md                 ← you are here (setup, run, journeys)
├── docker-compose.yml        ← Option B: full backend in Docker
├── backend/                  ← FastAPI API + workers (Python 3.11+)
│   ├── app/                     API, config, DB models, domain rules, services
│   ├── workers/                 script_worker (GPT text) · render_worker (video) · film_worker (stickman films)
│   ├── scripts/                 run_local.py (start everything) · demo_journey.py (learner flow)
│   ├── alembic/                 DB migrations
│   └── tests/                   58 tests (pytest)
├── android/                  ← the app (Kotlin, Jetpack Compose, Hilt, Room)
│   ├── app/                     entry point, navigation, bundled starter lessons (assets/lessons)
│   ├── monitor/                 reel detection, session rules, screen capture
│   ├── overlay/                 red budget line, check-in cards, in-feed player (drawn over Instagram)
│   ├── ml/                      on-device classifiers: vision · text · fusion (+ speech, llm experiments)
│   ├── feature/                 screens: onboarding · home · learn · insights · settings ("You")
│   └── core/                    model · data (Room) · network (API client) · player · designsystem
├── vendor/stickman-skill/    ← canvas stickman engine the films are written for (JS)
├── vendor/stickman-studio/   ← Kokoro voice + caption/compose tools used by the film renderer
├── config/                   ← remote-config defaults: ML thresholds, feature flags, Reels selectors
├── ml/                       ← offline ML tooling (training/benchmarking the reel classifier)
├── infra/poc/ · deploy/poc/  ← Terraform (AWS EC2 + S3 + SSM) · production Compose + boot script
└── designs/                  ← UI mockups, banner, film art
```

**Backend: key files**

| Concern | File |
|---|---|
| App factory, routers, `/trackVideoGen` page | `backend/app/main.py` |
| All settings (env vars) and defaults | `backend/app/config.py` |
| Database tables | `backend/app/models.py` · migrations in `backend/alembic/versions/` |
| Request/response schemas (strict: reject unknown fields) | `backend/app/schemas.py` |
| Endpoints: users/auth · topics/profile · feed/events · admin | `backend/app/api/` (`users.py`, `topics.py`, `lessons.py`, `admin.py`) |
| Lesson allocation, sharing between users, 5-lesson stock | `backend/app/services/generation.py` |
| Refill and stuck-job sweeps (scheduler) | `backend/app/services/sweeps.py`, `backend/app/cron.py` |
| Queue (Postgres-backed) and job retries | `backend/app/queues.py`, `backend/workers/common.py` |
| Syllabus → script → quality check (GPT) | `backend/workers/script_worker/pipeline.py`, `prompts.py` |
| Mock LLM (no key) | `backend/workers/script_worker/mock_llm.py` |
| Render job: video → quiz pause times → 720p → upload → manifest | `backend/workers/render_worker/pipeline.py` |
| Renderer choice (`film`, `stickman`, `placeholder`) | `backend/workers/render_worker/renderers.py` |
| Stickman film director (voice, plan, code, review, render, compose) | `backend/workers/film_worker/director.py`, `prompts.py` |
| What the app receives for a lesson | `backend/app/services/manifest.py` |
| Operator queue controls and history | `backend/app/services/admin_queue.py`, `backend/app/static/track_video_gen.html` |

**Android: key files**

| Concern | File |
|---|---|
| Detects the Reels screen and each new reel (Accessibility) | `android/monitor/src/main/kotlin/app/boomdoom/monitor/watcher/ReelWatcherService.kt` |
| Budget, check-in timing, snooze, pause rules | `android/monitor/src/main/kotlin/app/boomdoom/monitor/session/SessionEngine.kt` |
| Starts/stops monitoring, screen capture | `android/monitor/.../MonitorController.kt`, `monitor/.../capture/CaptureConsentActivity.kt` |
| Red line, check-in and budget cards over Instagram | `android/overlay/src/main/kotlin/app/boomdoom/overlay/OverlayHost.kt`, `OverlayUi.kt` |
| Reel classification on the phone | `android/ml/vision/.../FrameClassifier.kt`, `android/ml/text/.../CaptionClassifier.kt`, `android/ml/fusion/.../Fusion.kt` |
| Lessons, topics, offline cache, bundled starters | `android/core/data/src/main/kotlin/app/boomdoom/core/data/repo/LearningRepository.kt` |
| Backend sync (profile, feed, events) | `android/core/network/src/main/kotlin/app/boomdoom/core/network/SyncWorker.kt` |
| Lesson player with quiz pauses | `android/core/player/src/main/kotlin/app/boomdoom/core/player/` |
| Screens | `android/feature/{onboarding,home,learn,insights,settings}/` |
| Feed-isolation rule (monitor/overlay/ml can't reach the network) | `./gradlew checkFeedIsolation` (in `android/build-logic`) |

**Docs to read next:** [`stickmanPipelineE2E.md`](stickmanPipelineE2E.md) (how a lesson becomes a film),
[`deploy/poc/README.md`](deploy/poc/README.md) (deploy and admin API), [`BoomDoom_Tech_Spec.md`](BoomDoom_Tech_Spec.md)
(design; code comments cite its sections as §x), [`UX.md`](UX.md) (user stories US-xx).

---

## 2. Run the backend yourself

### Option A: Python (verified; about 3 minutes, no keys, no Docker)

**Prerequisites**
- Python **3.11 or 3.12** (3.13+ untested). On Ubuntu 22.04, which ships 3.10, use `uv`:
  `curl -LsSf https://astral.sh/uv/install.sh | sh && uv venv -p 3.12 backend/.venv`.
- `git`. Nothing else: Postgres is embedded (`pgserver`) and ffmpeg comes from `imageio-ffmpeg`.

```bash
git clone https://github.com/Hrushi11/BoomDoom.git && cd BoomDoom/backend
python3.12 -m venv .venv                     # or python3.11; or: uv venv -p 3.12 .venv
.venv/bin/pip install -e '.[dev]'            # with uv: uv pip install -p .venv/bin/python -e '.[dev]'
.venv/bin/python scripts/run_local.py --mock
```

That one command starts and connects everything:

| Process | What it does | Address |
|---|---|---|
| postgres | embedded PostgreSQL (`pgserver`); data in `backend/.data/pg` | unix socket |
| migrate | `alembic upgrade head`, once | |
| api | FastAPI (uvicorn) | **http://localhost:8000** (`/docs`, `/trackVideoGen`, `/static` for lesson files) |
| script worker | syllabus, lesson scripts, quality check | |
| render worker | lesson video, quiz pause times, 720p, thumbnail | |
| scheduler | refill sweep (10 min) and stuck-job sweep (5 min) | |

When it's ready you'll see:
```
   runner | API docs: http://localhost:8000/docs   lessons: http://localhost:8000/static   (Ctrl-C to stop)
   runner | tracker:  http://localhost:8000/trackVideoGen   password: boomdoom-admin
```
Ctrl-C stops everything. Delete `backend/.data/` for a clean database. Use another port with `--port 8001`.

**Mock mode (`--mock`).** Syllabi and scripts come from a templated mock LLM instead of OpenAI: they're readable,
topic-aware and pass the real validator, so they go through exactly the same pipeline. Videos are **placeholder
title cards** with timed silence (ffmpeg). Everything else is real: auth, topics, lesson sharing, the job queue,
retries, the feed, quiz pause times, events, refill and the tracker.

**Real lessons (OpenAI key).** Put `OPENAI_API_KEY=sk-...` in `backend/.env` (`cp .env.example .env`) and run
`.venv/bin/python scripts/run_local.py` without `--mock`. GPT then writes the syllabi and scripts; videos stay
placeholders. For real stickman films locally, use Option B with `RENDERER=film`.

### Option B: Docker Compose (the production image)

Prerequisites: Docker 24+ with Compose v2. The first build takes about 10 minutes: it downloads Chromium and the
Kokoro voice model.

```bash
git clone https://github.com/Hrushi11/BoomDoom.git && cd BoomDoom
docker compose up --build                                          # mock mode, no keys
# or, real GPT lessons and real stickman films (~10-20 min per film, ~$0.5-1 of OpenAI each):
OPENAI_API_KEY=sk-... RENDERER=film docker compose up --build
```

It starts Postgres 16, migrations, the API on **http://localhost:8000**, both workers and the scheduler. The tracker
password is `boomdoom-admin` (override with `ADMIN_PASSWORD`). `docker compose down -v` resets everything. Option B
uses the same Dockerfile that CI builds and deploys, but this Compose file itself hasn't been run on a clean machine
yet, which is why Option A is the primary path.

### Environment variables

Every setting is listed with comments in [`backend/.env.example`](backend/.env.example). **None is required for
mock mode.** The ones that change behaviour:

| Variable | Default | Purpose |
|---|---|---|
| `LLM_MODE` | `auto` | `auto` uses OpenAI when `OPENAI_API_KEY` is set, otherwise the mock. `mock` or `openai` forces one. |
| `OPENAI_API_KEY` | empty | Real GPT syllabi and scripts; required for `RENDERER=film` |
| `OPENAI_DAILY_COST_CAP_USD` | `10` | OpenAI calls pause for the day at this spend; jobs wait and retry |
| `RENDERER` | `placeholder` | `placeholder` (ffmpeg), `film` (stickman film director, production default), `stickman` (older renderer) |
| `ADMIN_PASSWORD` | `boomdoom-admin` locally | Sign-in for `/trackVideoGen`. With no password and no token, the admin routes don't exist. |
| `ADMIN_TOKEN` | empty | `X-Admin-Token` header for admin scripts |
| `DATABASE_URL`, `QUEUE_BACKEND`, `STORAGE_BACKEND`, `CDN_BASE_URL` | set by the runner | Postgres, queue (`db` or `sqs`), files (`local` or `s3`) |
| `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM` | generated locally | RS256 keys for app tokens |
| `FCM_SERVICE_ACCOUNT_JSON`, `COGNITO_*`, `CLOUDFRONT_*` | empty (off) | Push notifications, optional login and CDN signing: off in local runs |

---

## 3. Test logins

| User type | How to get in | Credentials |
|---|---|---|
| **Learner** (app user) | No sign-up. The app registers an anonymous device account on first launch. Over the API: `POST /v1/users` or `scripts/demo_journey.py`, which prints the tokens. | Bearer `access_token`; Swagger → **Authorize** → paste it |
| **Operator** (local) | http://localhost:8000/trackVideoGen | password `boomdoom-admin` |
| **Operator** (live) | https://100-22-80-77.sslip.io/trackVideoGen | password shared on request (not in the repo) |

Create a learner by hand:
```bash
curl -s -X POST localhost:8000/v1/users -H 'content-type: application/json' \
  -d '{"install_secret_hash":"'$(python3 -c 'import uuid;print(uuid.uuid4().hex*2)')'","app_version":"review","locale":"en-IN"}'
# -> {"user_id": "...", "access_token": "...", "refresh_token": "..."}
```

---

## 4. Journeys to try end to end

### Journey A: Learner over the API (what the app does)

One command plays the whole learner flow against a running backend, local or live:

```bash
cd backend
.venv/bin/python scripts/demo_journey.py                                     # local
.venv/bin/python scripts/demo_journey.py --api https://100-22-80-77.sslip.io --wait 1800  # live (real films)
.venv/bin/python scripts/demo_journey.py --topic "History of India" --topic "Psychology"
```

It prints each step:
1. **Register** a device account: `POST /v1/users`.
2. **Onboarding: pick topics**: `PUT /v1/users/{id}/lesson-profile`. Each topic starts as `preparing`.
3. **Wait for lessons.** Each topic gets a syllabus, then 5 lessons (script → render). `GET /v1/topics` shows
   `preparing` → `ready` with `ready_count`. In mock mode the first lesson is ready in **about 10–30 s**.
4. **Learn feed**: `GET /v1/feed`. Each lesson has a signed video URL, a thumbnail, quiz pauses (time, question,
   options, answer and feedback), a recap and go-deeper ideas. Open the video URL in a browser to watch it.
5. **Watch**: `POST /v1/events` with `lesson_started`, `interaction_answered` and `lesson_completed`.
6. **Feed moves on**: the completed lesson leaves the feed, and the scheduler tops the topic back up to 5.

Then keep going in Swagger as that user. The script prints the tokens; use **Authorize** in `/docs`:
- `POST /v1/topics` adds a topic. `PATCH /v1/topics/{id}` changes level or goal. `DELETE /v1/topics/{id}` removes one.
- Unsafe custom topics are refused by the safety check (try `{"name":"how to make a bomb","level":"beginner"}`).
- `POST /v1/lessons/{id}/reports` reports a lesson. `DELETE /v1/users/{id}` deletes the account and all its data
  (check progress with `GET /v1/deletions/{id}`).
- **Privacy rule:** every request schema rejects unknown fields, so feed or viewing data can't be sent. Try adding
  `"reels_watched": 5` to an event: the API returns 422.

### Journey B: Operator video generation tracker

1. Open `/trackVideoGen` on your local run and sign in with `boomdoom-admin`. The browser stays signed in
   for 30 days.
2. **Top counters:** films rendering, renders queued, scripts in progress, films made, failed. They refresh every 5 s.
3. **Rendering now:** each running film with its current step (voice → script → plan → code → review → render →
   compose; mock and placeholder runs skip most steps) and a **Stop** button. A stopped film halts at its next step
   and is never published.
4. **Up next:** the queue in pick-up order. **Prioritize** moves a job to the front; **Cancel** removes it.
5. **History:** every finished render, filterable by Done, Failed or Cancelled. Each row has the thumbnail, render
   time, and links to the video, thumbnail, `manifest.json` (what the app plays) and `project.json` (the film's
   notes, review findings and word timings).
6. The same controls over the API (also in Swagger under **admin**):
   `GET /v1/admin/queue`, `GET /v1/admin/history`, `POST /v1/admin/jobs/{id}/prioritize`,
   `POST /v1/admin/jobs/{id}/cancel?include_running=true`, and `POST /v1/admin/queue/cancel` with
   `{"user_id"|"topic_id"|"stage": ...}`.

Tip: run Journey A while the tracker is open to watch the jobs move through.

### Journey C: The Android app

**Install.** Download the APK above to an Android 10+ phone, or an emulator with Google APIs, API 30 or newer. On an
emulator, drag the APK onto the window or run `adb install BoomDoom-v0.2.1.apk`. The release APK talks to the
**live** backend. To use your local backend instead, build the app yourself (below) and run
`adb reverse tcp:8000 tcp:8000`.

1. **Onboarding:** welcome → your name → daily Reels budget → the content labels you care about → what to flag
   (for example rage-bait) → topics to learn (presets such as General Knowledge, History or Interview Prep, or your
   own) → **try a bundled lesson** → permissions.
2. **Permissions:** Accessibility (to count reels on Instagram's Reels screen), Display over other apps (the red
   budget line and check-in cards), and the screen-capture consent shown when monitoring starts. Classification runs
   on the phone, and **nothing about your feed is uploaded**.
3. **Home:** today's reels against your budget, monitoring status (pause or resume), next lesson.
4. **Learn:** your topics with their status (Preparing → Ready). Open a lesson: it plays full screen and **pauses for
   quizzes** (predict, multiple choice, true/false) with instant feedback, then shows the recap, which is saved to
   **Learned**. 8 bundled starter lessons play offline from the first launch.
5. **While scrolling Instagram Reels:** a thin red line at the top fills against your budget. Every few reels a
   check-in card shows what you've been watching and offers a lesson, which plays in the feed. Swipe up or press
   Back to return.
6. **Insights:** time and reels by category (comedy, finance, news, rage-bait, ads and so on) from on-device
   classification.
7. **You:** topics, budget, check-in settings, permissions, your data (export or delete account), About.

**No Instagram on the emulator? Use demo mode.** Open **You → About** and tap the version 7 times ("Demo mode on").
Demo buttons then simulate reels, so the red line, check-ins (every 3 reels) and the budget card all work without
Instagram. Grant **Display over other apps** first.

**Build the app yourself** (optional; JDK 17–21 and the Android SDK, API 35):
```bash
cd android
echo "boomdoom.apiBaseUrl=http://localhost:8000/" >> local.properties   # or the live URL
./gradlew :app:assembleProdDebug          # app/build/outputs/apk/prod/debug/app-prod-debug.apk
./gradlew :app:assembleDemoDebug          # demo flavour (app.boomdoom.demo)
adb install -r app/build/outputs/apk/prod/debug/app-prod-debug.apk && adb reverse tcp:8000 tcp:8000
```

---

## 5. Tests

```bash
cd backend
.venv/bin/python -m pytest -q        # 58 tests: real embedded Postgres, migrations and workers; fake LLM and renderer
.venv/bin/ruff check . && .venv/bin/ruff format --check .
cd ../android && ./gradlew checkFeedIsolation :core:common:test :ml:fusion:test :monitor:testDebugUnitTest
```
CI runs both on every push (`.github/workflows/backend.yml`, `android.yml`). `deploy.yml` builds the production image.

---

## 6. How it works (short)

```
Android app ── topics, lesson events only ──►  FastAPI (/v1)  ──► Postgres job queue
  (reels never leave the phone)                                  │
                                    script worker ◄──────────────┤  syllabus → lesson script → quality check (GPT)
                                    render worker ◄──────────────┘  film: voice → plan → code → review → render → compose
                                          │                         → quiz pause times → 720p + thumbnail
                                          └──► S3 (or /static locally) ──► feed ──► app plays with quiz pauses
```
- **On the phone:** an Accessibility service detects the Reels screen; ONNX Runtime models classify each reel
  (SigLIP 2 image head plus a MiniLM caption model) into categories for Insights and check-ins.
- **Lessons:** each topic keeps 5 ready lessons. Identical lessons (same topic, concept and engine) are shared
  between users, and jobs retry with backoff. A daily OpenAI cost cap protects spend.
- **Films:** GPT writes a JavaScript film for a canvas stickman engine (`vendor/stickman-skill`). It's voiced with
  Kokoro, reviewed from still frames by GPT vision, rendered frame by frame in headless Chromium, then captioned.
  Details: [`stickmanPipelineE2E.md`](stickmanPipelineE2E.md) and [`stickManCustom.md`](stickManCustom.md).
- **Specs:** [`BoomDoom_Product_Spec.md`](BoomDoom_Product_Spec.md), [`UX.md`](UX.md),
  [`BoomDoom_Tech_Spec.md`](BoomDoom_Tech_Spec.md). Deployment: [`deploy/poc/README.md`](deploy/poc/README.md),
  [`infra/poc`](infra/poc) (Terraform).

---

## 7. What is simulated, mocked or not finished

| Area | Status |
|---|---|
| **Mock mode** (`--mock` / `LLM_MODE=mock`) | Lesson text is templated, not GPT-written; videos are placeholder title cards. Use the live server or an OpenAI key for real lessons and films. |
| **Demo mode** (app) | Buttons simulate reels for emulators without Instagram. Off by default. |
| **Reel classification** | Runs on the phone. Accuracy is POC-level: a lot of content still lands in "other", and categories come from a small trained head. |
| **Instagram only** | Only Instagram Reels is monitored. YouTube Shorts and others are not supported yet. Reels-screen detection depends on Instagram's layout and may break when Instagram changes it. |
| **Film generation time** | Real films take 10–20 minutes each (three render at once on the server). The first lesson of a new topic takes that long; shared lessons are instant. |
| **Push notifications** (FCM) | Implemented, but off: needs Firebase credentials. |
| **Account login** (Cognito) | Implemented, but off: accounts are anonymous per device. |
| **iOS** | Not built. |
| **Single-host deployment** | One EC2 instance runs Postgres, the queue and the workers, with no high availability. A push to `main` redeploys within about 2 minutes and restarts any film in progress. |
| **Painted stages for films** | Experimental branch (`feature/film-assets`, not in `main`): new backgrounds, props and 8 characters for more varied films. |

---

## 8. Troubleshooting

- **`run_local.py` exits with "backend/.env not found":** add `--mock`, or `cp .env.example .env`.
- **Port 8000 in use:** `--port 8001` (then `demo_journey.py --api http://localhost:8001`).
- **`pip install` fails on Python 3.10:** use Python 3.11 or 3.12 (see `uv` above).
- **A topic stays `preparing` with a key set:** open `/trackVideoGen`. With `RENDERER=film`, a film takes 10–20 min.
  If the error is "daily OpenAI cost cap reached", raise `OPENAI_DAILY_COST_CAP_USD`.
- **The app shows no lessons:** check the Learn tab's topic status, then Home. The release APK uses the live server;
  a local build needs `adb reverse tcp:8000 tcp:8000`.
