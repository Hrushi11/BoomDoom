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
  core/           model · common · designsystem · data (Room/DataStore) · network (Retrofit; lesson data only)
  monitor/        ReelWatcher (accessibility), SessionEngine, MonitorService, capabilities
  overlay/        Red-line budget bar, flag tag, check-in & budget cards
  ml/             vision (SigLIP 2) · text (EmbeddingGemma + OCR) · speech · fusion · llm (LFM2-VL)
  feature/        onboarding · home · learn · insights · settings
  build-logic/    Gradle convention plugins
backend/        FastAPI API + script worker (OpenAI) + render worker (Stickman Studio) (§8)
config/         Remote-config defaults: selectors, ML thresholds, models manifest, feature flags (App. C)
ml/             Offline, team-only ML tooling (§9.6)
infra/          Terraform modules + demo/dev/prod envs (§9.4)
deploy/demo/    Docker Compose for the hackathon `demo` env (§9.1)
.github/        CI: backend, android, infra (§9.5)
```

## Backend

```bash
cd backend
source .venv/bin/activate          # Python 3.11 venv, already created with deps installed
cp .env.example .env
pytest -q                          # unit + API tests (no DB needed)
ruff check . && ruff format --check .

# With Postgres running (e.g. `docker compose -f deploy/demo/docker-compose.yml up db`):
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

The debug build points at `http://10.0.2.2:8000/` (the host machine from the emulator).

## Where things deviate from the Tech Spec

| Spec | Scaffold | Why |
|---|---|---|
| Python 3.12 (§2.2) | venv on 3.11; code targets ≥ 3.11; CI uses 3.12 | 3.12 isn't installed on this machine |
| `:core:data` includes the network client (§3.1) | split into `:core:data` (local) and `:core:network` | Makes the feed-isolation rule (§3.2) a module-level check instead of a class-level one |
| `job` table (§8.4) | extra `topic_id` column | Script jobs need to find their topic before a lesson/syllabus row exists |
| Proto DataStore (§6.1) | Preferences DataStore behind `SettingsRepository` | Simpler setup; can be swapped without touching callers |
| Firebase (FCM, Crashlytics) | not wired yet | Needs `google-services.json` |

## Status

Implemented and unit-tested: SessionEngine (check-ins, back-off, snooze, budget card, pause/timeout), reel-transition debounce, day key with the 4 AM reset, signal fusion and thresholds, lesson sharing/stock rules, lesson lifecycle, script validation (Appendix A), pause-time extraction, the API v1 surface, and the privacy check that rejects feed data.

Stubbed with `TODO(owner)` markers (owners follow the §14.1 team split, A–D): accessibility selectors (spike S1), MediaProjection frame sampling, audio capture, ML runtimes, overlay cards, lesson player, SQS worker loops, the Stickman Studio integration, CloudFront signing, and Terraform modules.
