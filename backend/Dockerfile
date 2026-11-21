# One image for the API, the script worker and the render worker (POC: single EC2 host).
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg fonts-liberation fonts-noto-core curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY pyproject.toml ./
COPY app ./app
COPY workers ./workers
RUN pip install --no-cache-dir .

# Optional: install the Stickman Studio fork into the image, then set RENDERER=stickman.
#   docker build --build-arg STICKMAN_PIP_SPEC="git+https://github.com/<org>/stickman-studio@<commit>" .
ARG STICKMAN_PIP_SPEC=""
RUN if [ -n "$STICKMAN_PIP_SPEC" ]; then pip install --no-cache-dir "$STICKMAN_PIP_SPEC"; fi

COPY alembic ./alembic
COPY alembic.ini ./

RUN useradd --create-home --uid 10001 app && mkdir -p /tmp/boomdoom-render && chown app /tmp/boomdoom-render
USER app

ENV PYTHONUNBUFFERED=1
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD curl -fsS http://localhost:8000/healthz || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]
