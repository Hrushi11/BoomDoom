# BoomDoom backend (POC)

FastAPI API + workers that turn a user's topics into stickman lessons (Tech Spec §8). It never receives feed data:
every request schema rejects unknown fields.

```
app/            API (routers in app/api), domain rules, services, config
workers/        python -m workers script   -> script-jobs (syllabus, script, QA via OpenAI) + deletion-jobs
                python -m workers render   -> render-jobs (render, pause times, 720p, thumbnail, S3)
app/cron.py     python -m app.cron loop    -> refill sweep (10 min) + stuck-job sweeper (5 min)
alembic/        migrations (0001 = initial schema)
tests/          real Postgres (embedded), real migrations, real worker code with a fake LLM/renderer
```

## Lesson pipeline

```
PUT lesson-profile / POST topics
  -> syllabus job (safety check for custom topics, then gpt-6.2-sol)       [script-jobs]
  -> allocate 5 lessons: reuse shared lessons first, generate the shortfall
  -> script job per lesson (gpt-6.1-terra -> validate -> gpt-6-luna QA)      [script-jobs]
  -> render job (Stickman Studio or placeholder -> pause times -> ffmpeg -> S3) [render-jobs]
  -> lesson ready -> topic ready -> FCM push -> GET /v1/feed
events (completed/skipped) -> refill when stock < 3
```

Jobs are claimed atomically, so a duplicate message does nothing. Failures retry with backoff, up to 3 attempts.
On SIGTERM, a worker hands its current job back without spending an attempt. OpenAI calls stop for the day once
spend reaches `OPENAI_DAILY_COST_CAP_USD`, and pause for 5 minutes after 10 consecutive failures.

## Run locally

One command runs the whole stack like the AWS host: embedded Postgres, migrations, API, both workers and the scheduler.

```bash
cd backend
cp .env.example .env            # then set OPENAI_API_KEY
.venv/bin/python scripts/run_local.py
# API: http://localhost:8000/docs     Ctrl-C stops everything
```

- **Data** lives in `backend/.data/` (Postgres, lesson files, local JWT keys). Delete the folder to start fresh.
- **Rendering** uses Stickman Studio automatically when `../../Stickman-Studio` (next to the BoomDoom folder)
  has `boomdoom_entry.py` and a `.venv`; otherwise it uses the ffmpeg placeholder. `--placeholder` forces it.
- **App on the emulator:** run `adb reverse tcp:8000 tcp:8000`. The app's default API URL (`10.0.2.2:8000`) and the
  lesson URLs (`localhost:8000`) then both reach this machine.
- **App on a phone:** add `boomdoom.apiBaseUrl=http://localhost:8000/` to `android/local.properties`, rebuild, and
  run `adb reverse tcp:8000 tcp:8000` with the phone connected.

Tests: `.venv/bin/pytest -q` (starts its own embedded Postgres).

## Stickman Studio (lesson renderer)

The `boomdoom` branch of the fork, checked out at `../../Stickman-Studio`. It has its own virtualenv, and the
render worker calls `boomdoom_entry.py` in a subprocess. One-time setup:

```bash
cd ../../Stickman-Studio
python3.11 -m venv .venv
.venv/bin/pip install Pillow requests numpy tqdm soundfile onnxruntime tokenizers kokoro-onnx imageio-ffmpeg
ln -sf "$(.venv/bin/python -c 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())')" .venv/bin/ffmpeg
mkdir -p models/kokoro && cd models/kokoro && \
  curl -LO https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.int8.onnx && \
  curl -LO https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin
```

Patches on the `boomdoom` branch (Tech Spec §8.8):
- captions fall back to the bundled Noto Sans (Windows-only font lookup)
- real NVENC probe in the Python render path
- SFX and music mixing argument bugs
- missing-glyph boxes in titles
- `boomdoom_entry.py`, the headless entry point

Measured on an M-series Mac: about 23 s to render a 45 s lesson. OpenAI cost is about $0.008 per lesson.

## Deploy (AWS POC)

See [`../deploy/poc/README.md`](../deploy/poc/README.md).

## Known POC limits

- **Rendering on AWS:** the POC image still uses the placeholder renderer. Getting Stickman Studio onto the host is
  the next deploy step (the fork, its virtualenv and the Kokoro model files in the image).
- **OpenAI temperature:** the gpt-6 models only accept the default temperature, so the per-step values in Tech Spec
  §8.7 aren't sent.
- **Presigned URLs:** S3 presigned URLs are signed with the instance's temporary credentials, so they can expire
  before 24 h (typically after about 6 h). The app refreshes the feed every 6 h and plays prefetched files from
  disk, so this is fine for a POC. Configure CloudFront signing for longer-lived URLs.
- **Single host:** Postgres, the queue and the workers all share one EC2 instance. Backups are the EBS volume only.
- **Optional login (Cognito)** is implemented, but stays disabled until a user pool is configured.
