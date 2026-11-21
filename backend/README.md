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

```bash
source .venv/bin/activate
cp .env.example .env                 # add OPENAI_API_KEY to run the script worker
pytest -q                            # starts an embedded Postgres; no Docker needed
```

Against a local Postgres:

```bash
alembic upgrade head
uvicorn app.main:app --reload        # http://localhost:8000/docs
python -m workers script             # needs OPENAI_API_KEY
python -m workers render             # needs ffmpeg on PATH
python -m app.cron loop
```

## Deploy (AWS POC)

See [`../deploy/poc/README.md`](../deploy/poc/README.md).

## Known POC limits

- **Rendering:** the default is `RENDERER=placeholder`, which makes solid-colour videos. That is enough to exercise
  the pipeline and the player. Real stickman videos need the Stickman Studio fork installed in the image and
  `RENDERER=stickman`; the contract the fork must implement is in `workers/render_worker/renderers.py`.
- **Presigned URLs:** S3 presigned URLs are signed with the instance's temporary credentials, so they can expire
  before 24 h (typically after about 6 h). The app refreshes the feed every 6 h and plays prefetched files from
  disk, so this is fine for a POC. Configure CloudFront signing for longer-lived URLs.
- **Single host:** Postgres, the queue and the workers all share one EC2 instance. Backups are the EBS volume only.
- **Optional login (Cognito)** is implemented, but stays disabled until a user pool is configured.
