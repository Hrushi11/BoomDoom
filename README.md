# BoomDoom

Doom-scroll awareness for Android: count reels against a daily budget (the red line), check in every *n* reels, classify what you watched **on the phone**, and swap scrolling for short interactive stickman lessons.

| Doc | What it covers |
|---|---|
| [`BoomDoom_Product_Spec.md`](BoomDoom_Product_Spec.md) | What and why |
| [`UX.md`](UX.md) | User stories (US-xx) and screens |
| [`BoomDoom_Tech_Spec.md`](BoomDoom_Tech_Spec.md) | How it's built (sections are referenced as §x in code) |
| [`prompts_for_design.md`](prompts_for_design.md), [`designs/`](designs) | Visual direction |

**Core rule:** feed data never leaves the phone. The backend only sees learning preferences and lesson events. This is enforced in code: `./gradlew checkFeedIsolation` on Android, and request schemas that reject unknown fields on the backend.

## Repository layout

```
android/        Kotlin + Jetpack Compose app (Tech Spec §3)
  app/            Application, navigation, Hilt, WorkManager
  core/           model · common · designsystem · player · data (Room/DataStore) · network (Retrofit; lesson data only)
  monitor/        ReelWatcher (accessibility), SessionEngine, MonitorService, capabilities
  overlay/        Red-line budget bar, flag tag, check-in & budget cards, in-feed lesson player
  ml/             vision (SigLIP 2) · text (EmbeddingGemma + OCR) · speech · fusion · llm (LFM2-VL)
  feature/        onboarding · home · learn · insights · settings
  build-logic/    Gradle convention plugins
backend/        FastAPI API + script worker (OpenAI) + render worker (Stickman Studio) (§8) — see backend/README.md
config/         Remote-config defaults: selectors, ML thresholds, models manifest, feature flags (App. C)
ml/             Offline, team-only ML tooling (§9.6)
infra/poc/      Terraform for the AWS POC: one EC2 host, S3, ECR, SSM secrets
deploy/poc/     Docker Compose stack + deploy script for that host — see deploy/poc/README.md
.github/        CI: backend, android
```

## Backend

```bash
cd backend
source .venv/bin/activate          # Python 3.11 venv, already created with deps installed
cp .env.example .env
pytest -q                          # unit + API tests (no DB needed)
ruff check . && ruff format --check .

# With a local Postgres:
alembic upgrade head
uvicorn app.main:app --reload      # http://localhost:8000/docs
```

Recreate the venv: `python3.11 -m venv .venv && .venv/bin/pip install -e '.[dev]'`.

## Android

Open `android/` in Android Studio, or from the command line (JDK 17):

```bash
cd android
./gradlew checkFeedIsolation                      # monitor/overlay/ml can't reach the network
./gradlew :core:common:test :ml:fusion:test :monitor:testDebugUnitTest
./gradlew :app:assembleProdDebug                  # or :app:assembleDemoDebug for demo mode (§14.3)
```

**Backend URL.** The default is `http://10.0.2.2:8000/` (a backend on your laptop, seen from the emulator). To use the
AWS POC instead, add this to `android/local.properties`:

```
boomdoom.apiBaseUrl=https://<your-elastic-ip-with-dashes>.sslip.io/
```

**Trying it without Instagram.** Open You → About and tap the version 7 times to turn on demo mode. Its buttons
simulate reels, so the red line, check-ins (every 3 reels in demo mode) and the budget card can all be shown on an
emulator. Grant **Display over apps** and choose a lesson from a check-in to try the in-feed player. Swipe up or use
Android Back to return to the screen underneath.

**Assets.** Stickman illustrations are cropped from the mockups in `designs/` (`core/designsystem/.../drawable-nodpi`).
The 8 starter lessons in `app/src/main/assets/lessons` come from `tools/make_starter_lessons.py`. They are silent
placeholder videos with timed captions until real Stickman Studio renders replace them. The font is Plus Jakarta Sans
(OFL; licence in `core/designsystem`).

## Where things deviate from the Tech Spec

| Spec | Scaffold | Why |
|---|---|---|
| Python 3.12 (§2.2) | venv on 3.11; code targets ≥ 3.11; CI and the Docker image use 3.12 | 3.12 isn't installed on this machine |
| ECS + RDS + SQS + CloudFront (§9) | POC: one EC2 host (Compose), Postgres-backed queue, S3 presigned URLs | POC scope. The code still supports SQS (`QUEUE_BACKEND=sqs`) and CloudFront signing |
| EventBridge → scheduler endpoints (§8.1) | `python -m app.cron loop` on the host | Single host; no public internal endpoints |
| `job`, `user_lesson`, `syllabus` tables (§8.4) | `job.topic_id`, `user_lesson.topic_id/assigned_at`, `syllabus.status`, plus `queue_message` and `daily_cost` tables | Jobs and lessons need topic links; shared syllabi need a build status; the DB queue and the OpenAI cost cap need storage |
| `:core:data` includes the network client (§3.1) | split into `:core:data` (local) and `:core:network` | Makes the feed-isolation rule (§3.2) a module-level check instead of a class-level one |
| Proto DataStore (§6.1) | Preferences DataStore behind `SettingsRepository` | Simpler setup; can be swapped without touching callers |
| Firebase (FCM, Crashlytics) | not wired yet | Needs `google-services.json` |

## Status

Implemented and unit-tested: SessionEngine (check-ins, back-off, snooze, budget card, pause/timeout), reel-transition debounce, day key with the 4 AM reset, signal fusion and thresholds, lesson sharing/stock rules, lesson lifecycle, script validation (Appendix A), pause-time extraction, the API v1 surface, and the privacy check that rejects feed data.

**Backend: complete for the POC.** Everything in §8 is implemented: the API, the lesson pipeline (syllabus → script → QA → render → feed), sharing, refill, retries, account deletion, rate limits, the OpenAI cost cap, push notifications, and AWS deployment. Real stickman rendering needs the Stickman Studio fork (see backend/README.md).

**Android: the POC loop works end to end.** It covers onboarding (6 steps + permissions), reel counting via
Accessibility, the red-line bar, check-in and budget cards, Home, Learn with the interactive player (predict,
multiple choice, true/false), recap and Learned, My topics, Insights, You, settings, account deletion, backend sync,
and demo mode.

**Not built yet:** content classification. That needs screen capture plus the on-device models (SigLIP 2, OCR,
speech), so until then every reel is recorded as "other" and no category breakdown is shown. Reels-screen detection
uses a layout heuristic plus unverified view ids (`monitor/src/main/assets/reels_selectors.json`); spike S1 on a real
phone is needed to confirm them.
