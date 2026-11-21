# BoomDoom POC image: API, workers and the Stickman Studio renderer in one image.
# Built by GitHub Actions (.github/workflows/deploy.yml) from the repo root and pushed to ghcr.io.
#   docker build -f deploy/poc/Dockerfile .
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg fonts-liberation fonts-noto-core curl \
    && rm -rf /var/lib/apt/lists/*

# ---- Stickman Studio in its own virtualenv (render worker calls boomdoom_entry.py in a subprocess) ----
WORKDIR /opt/stickman
RUN python -m venv .venv && .venv/bin/pip install --no-cache-dir \
      "Pillow>=10" "requests>=2.31" "numpy>=1.24" "tqdm>=4.65" "soundfile>=0.12" "onnxruntime>=1.16" \
      "tokenizers>=0.19" "kokoro-onnx>=0.3"
# Kokoro voice model (~115 MB), cached in its own layer
RUN mkdir -p models/kokoro && cd models/kokoro \
    && curl -fsSL --retry 3 -O https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.int8.onnx \
    && curl -fsSL --retry 3 -O https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin
COPY vendor/stickman-studio/ /opt/stickman/

# ---- BoomDoom backend ----
WORKDIR /srv
COPY backend/pyproject.toml ./
COPY backend/app ./app
COPY backend/workers ./workers
RUN pip install --no-cache-dir .
COPY backend/alembic ./alembic
COPY backend/alembic.ini ./
COPY config ./config

RUN useradd --create-home --uid 10001 app \
    && mkdir -p /tmp/boomdoom-render && chown app /tmp/boomdoom-render \
    && chown -R app /opt/stickman
USER app

ENV PYTHONUNBUFFERED=1 \
    RENDERER=stickman \
    STICKMAN_STUDIO_DIR=/opt/stickman \
    STICKMAN_PYTHON=/opt/stickman/.venv/bin/python
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD curl -fsS http://localhost:8000/healthz || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "*"]
