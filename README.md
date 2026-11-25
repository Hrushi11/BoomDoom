<p align="center">
  <img src="designs/boomdoom_github_banner.png" width="100%" alt="BoomDoom: Turn doom into boom.">
</p>

<p align="center">Doom-scroll awareness for Android that swaps some of your Reels time for 30-second stickman lessons.</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-2EC4B6">
  <img alt="Backend" src="https://img.shields.io/badge/Backend-FastAPI%20%2B%20Postgres-FF6B3D">
  <img alt="On-device ML" src="https://img.shields.io/badge/On--device%20ML-ONNX%20Runtime-14151A">
  <img alt="Privacy" src="https://img.shields.io/badge/Feed%20data-never%20leaves%20the%20phone-0E8A78">
</p>

- **The red line.** A thin budget bar sits over Instagram Reels and counts every reel against your daily budget.
- **Check-ins, not locks.** Every few reels BoomDoom shows what you've been watching and offers a quick lesson instead.
- **On-device understanding.** Reels are classified (comedy, finance, rage-bait, ads...) on the phone. Nothing about
  your feed is ever uploaded.
- **Stickman lessons.** Short 9:16 films written by GPT and animated in code, with voice, captions and quiz pauses.

| Doc | What it covers |
|---|---|
| [`BoomDoom_Product_Spec.md`](BoomDoom_Product_Spec.md) | What and why |
| [`UX.md`](UX.md) | User stories (US-xx) and screens |
| [`BoomDoom_Tech_Spec.md`](BoomDoom_Tech_Spec.md) | How it's built (sections are referenced as §x in code) |
| [`prompts_for_design.md`](prompts_for_design.md), [`designs/`](designs) | Visual direction |

**Core rule:** feed data never leaves the phone. The backend only sees learning preferences and lesson events. This is enforced in code: `./gradlew checkFeedIsolation` on Android, and request schemas that reject unknown fields on the backend.

## Demo

https://github.com/user-attachments/assets/22aee885-2cf5-4aca-9b73-373150c6a956

3.5 minutes: Home, scrolling goals, a stickman lesson playing, Insights and Learn. Full-quality file: [`AppDemo.mp4`](AppDemo.mp4).

## Designs

### Onboarding
<table>
  <tr>
    <td align="center" width="33%"><img src="designs/01.png" width="100%" alt="Welcome"><br><sub>Welcome</sub></td>
    <td align="center" width="33%"><img src="designs/02.png" width="100%" alt="Your scroll budget"><br><sub>Your scroll budget</sub></td>
    <td align="center" width="33%"><img src="designs/04.png" width="100%" alt="What to see less of"><br><sub>What to see less of</sub></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="designs/05.png" width="100%" alt="Topics to learn"><br><sub>Topics to learn</sub></td>
    <td align="center" width="33%"><img src="designs/06.png" width="100%" alt="A 45-second first lesson"><br><sub>A 45-second first lesson</sub></td>
    <td align="center" width="33%"><img src="designs/07.png" width="100%" alt="Permissions"><br><sub>Permissions</sub></td>
  </tr>
</table>

### While you scroll
<table>
  <tr>
    <td align="center" width="33%"><img src="designs/v1.png" width="100%" alt="The red line over Reels"><br><sub>The red line over Reels</sub></td>
    <td align="center" width="33%"><img src="designs/v4.png" width="100%" alt="A flagged reel"><br><sub>A flagged reel</sub></td>
    <td align="center" width="33%"><img src="designs/v5.png" width="100%" alt="Check-in card"><br><sub>Check-in card</sub></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="designs/v6.png" width="100%" alt="Understood on this phone"><br><sub>Understood on this phone</sub></td>
    <td align="center" width="33%"><img src="designs/H2.png" width="100%" alt="Over budget"><br><sub>Over budget</sub></td>
    <td align="center" width="33%"><img src="designs/h3.png" width="100%" alt="Screen understanding off"><br><sub>Screen understanding off</sub></td>
  </tr>
</table>

### Home and Insights
<table>
  <tr>
    <td align="center" width="33%"><img src="designs/H1.png" width="100%" alt="Home"><br><sub>Home</sub></td>
    <td align="center" width="33%"><img src="designs/i1.png" width="100%" alt="Today"><br><sub>Today</sub></td>
    <td align="center" width="33%"><img src="designs/i3.png" width="100%" alt="Last 7 days"><br><sub>Last 7 days</sub></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="designs/i2.png" width="100%" alt="Fix a label"><br><sub>Fix a label</sub></td>
    <td align="center" width="33%"><img src="designs/l8.png" width="100%" alt="Suggested topics"><br><sub>Suggested topics</sub></td>
    <td align="center" width="33%"><img src="designs/s2.png" width="100%" alt="Notifications"><br><sub>Notifications</sub></td>
  </tr>
</table>

### Learn
<table>
  <tr>
    <td align="center" width="33%"><img src="designs/l1.png" width="100%" alt="Learn"><br><sub>Learn</sub></td>
    <td align="center" width="33%"><img src="designs/l2.png" width="100%" alt="Lesson player"><br><sub>Lesson player</sub></td>
    <td align="center" width="33%"><img src="designs/l3.png" width="100%" alt="Predict the answer"><br><sub>Predict the answer</sub></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="designs/l4.png" width="100%" alt="True or false"><br><sub>True or false</sub></td>
    <td align="center" width="33%"><img src="designs/l5.png" width="100%" alt="Saved to Learned"><br><sub>Saved to Learned</sub></td>
    <td align="center" width="33%"><img src="designs/l6.png" width="100%" alt="Learned"><br><sub>Learned</sub></td>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="designs/l7.png" width="100%" alt="My topics"><br><sub>My topics</sub></td>
    <td align="center" width="33%"><img src="designs/l9.png" width="100%" alt="Report a mistake"><br><sub>Report a mistake</sub></td>
    <td align="center" width="33%"><img src="designs/s1.png" width="100%" alt="Keep your lessons"><br><sub>Keep your lessons</sub></td>
  </tr>
</table>

### You
<table>
  <tr>
    <td align="center" width="33%"><img src="designs/y1.png" width="100%" alt="You"><br><sub>You</sub></td>
    <td align="center" width="33%"><img src="designs/y2.png" width="100%" alt="Scrolling goals"><br><sub>Scrolling goals</sub></td>
    <td align="center" width="33%"><img src="designs/y4.png" width="100%" alt="Privacy and data"><br><sub>Privacy and data</sub></td>
  </tr>
</table>

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

## Build a downloadable APK

```bash
cd android
./build-apk.sh --create-key          # one time: make the release key + keystore.properties
./build-apk.sh --v0.2.0              # set version 0.2.0 (versionCode +1) → dist/BoomDoom-v0.2.0.apk
./build-apk.sh --v0.2.0 --install    # ...and install on the phone connected over adb
./build-apk.sh --demo                # demo flavour instead → dist/BoomDoom-v<version>-demo.apk
./build-apk.sh                       # rebuild the current version (asks before overwriting)
```

- Signed with the release key `~/.android/boomdoom-release.jks`; its passwords are in
  `android/keystore.properties`. Both are git-ignored. **Back them up**: every future
  update must be signed with the same key, or phones refuse to install it.
- The script sets `versionName` and bumps `versionCode` in `android/app/build.gradle.kts` for you.
- It builds the `prod` flavour (`app.boomdoom`) by default. The backend address is baked in from
  `boomdoom.apiBaseUrl` in `android/local.properties`, and the script refuses to build while it
  points at the emulator (`10.0.2.2`) or `localhost`.
- It uses JBR 21 (`~/Library/Java/JavaVirtualMachines/jbr-21.0.11`), because Gradle 8.11 can't run
  on Android Studio's bundled JDK 25. Set `JAVA_HOME_OVERRIDE` to use another JDK 17-21.
- It checks the APK is 16 KB page-aligned, which Android 15+ requires for the ONNX Runtime libraries.
- Installing on a phone: open the APK, allow "Install unknown apps" for the browser/Files app,
  and choose "Install anyway" if Play Protect warns (normal for apps outside the Play Store).
- A phone that has the *debug* build (from Android Studio) must uninstall it first:
  the two builds are signed with different keys.

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

**Reel classification is in benchmarking.** The [ML harness](ml/README.md) runs caption-first classification,
five-second visual previews and optional speech on local test clips. Its Android runner measures caption and frame
inference on a phone. Live capture and model integration in the monitoring app are pending, so live reels are
still recorded as "other". Reels-screen detection
uses a layout heuristic plus unverified view ids (`monitor/src/main/assets/reels_selectors.json`); spike S1 on a real
phone is needed to confirm them.

## License

BoomDoom is released under the [MIT License](LICENSE).

Third-party parts keep their own licences:

| Part | Licence |
|---|---|
| [`vendor/stickman-skill`](vendor/stickman-skill) (stickman film engine), by michxlai | MIT ([`LICENSE`](vendor/stickman-skill/LICENSE)) |
| [`vendor/stickman-studio`](vendor/stickman-studio) (voice, captions, compose), by Atharv Hatwar | MIT ([`LICENSE`](vendor/stickman-studio/LICENSE)) |
| Sound effects in `vendor/stickman-skill/sfx/`, by [Kenney](https://www.kenney.nl) | CC0 |
| Plus Jakarta Sans font (`android/core/designsystem`) | SIL Open Font License 1.1 |
| Kokoro voice model (downloaded at build time, not in this repo) | Apache 2.0 |
