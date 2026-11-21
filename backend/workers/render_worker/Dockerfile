# boomdoom-render (Tech Spec §8.8). Build from backend/:  docker build -f workers/render_worker/Dockerfile .
FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg fonts-liberation fonts-noto-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY pyproject.toml ./
COPY app ./app
COPY workers ./workers
RUN pip install --no-cache-dir .
# TODO: pin and install the Stickman Studio fork + Kokoro ONNX model files (kokoro-v1.0.int8.onnx, voices-v1.0.bin)
CMD ["python", "-m", "workers.render_worker.main"]
