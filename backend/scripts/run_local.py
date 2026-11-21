"""Run the whole backend locally, mirroring the AWS POC host (deploy/poc) without Docker.

    .venv/bin/python scripts/run_local.py [--port 8000] [--public-url http://localhost:8000]

Starts:
  postgres   embedded PostgreSQL (pgserver); data kept in backend/.data/pg
  migrate    alembic upgrade head (once)
  api        uvicorn on 0.0.0.0:<port>; lesson files served from /static
  script     python -m workers script   (OpenAI: syllabus, scripts, QA; account deletion)
  render     python -m workers render   (placeholder renderer + ffmpeg)
  scheduler  python -m app.cron loop    (refill + stuck-job sweeps)

Secrets (OPENAI_API_KEY, ...) come from backend/.env. The values below override the database,
queue and storage settings so nothing else is needed. Ctrl-C stops everything.

Phone/emulator: run `adb reverse tcp:<port> tcp:<port>` and use http://localhost:<port>/ as the API URL.
"""

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
DATA = ROOT / ".data"
PY = sys.executable
COLORS = {"api": "36", "script": "35", "render": "33", "scheduler": "32", "migrate": "34", "runner": "1"}


def say(name: str, line: str) -> None:
    sys.stdout.write(f"\033[{COLORS.get(name, '0')}m{name:>9}\033[0m | {line.rstrip()}\n")
    sys.stdout.flush()


def start_postgres() -> tuple[object, str]:
    import pgserver

    srv = pgserver.get_server(DATA / "pg", cleanup_mode="stop")
    if "boomdoom" not in srv.psql("select datname from pg_database;"):
        srv.psql("create database boomdoom;")
    host = srv.get_uri().split("host=")[1]
    return srv, f"postgresql+psycopg://postgres@/boomdoom?host={host}"


def local_jwt_keys() -> dict[str, str]:
    """Stable signing keys across restarts (otherwise every restart logs every client out).

    Uses keys from backend/.env if set; else creates them once in .data/jwt/.
    """
    from app.config import get_settings

    if get_settings().jwt_private_key_pem:
        return {}
    d = DATA / "jwt"
    priv, pub = d / "private.pem", d / "public.pem"
    if not priv.exists():
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import rsa

        d.mkdir(parents=True, exist_ok=True)
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        priv.write_bytes(
            key.private_bytes(
                serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
            )
        )
        pub.write_bytes(
            key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
        )
        priv.chmod(0o600)
    return {"JWT_PRIVATE_KEY_PEM": priv.read_text(), "JWT_PUBLIC_KEY_PEM": pub.read_text()}


def ffmpeg_path() -> str:
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def pump(name: str, proc: subprocess.Popen) -> None:
    assert proc.stdout is not None
    for line in proc.stdout:
        say(name, line)
    say("runner", f"{name} exited with code {proc.wait()}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--public-url", default=None, help="Base URL devices use to reach this machine")
    ap.add_argument(
        "--stickman-dir",
        default=str(ROOT.parent.parent / "Stickman-Studio"),
        help="Stickman Studio fork checkout (default: next to the BoomDoom folder)",
    )
    ap.add_argument("--placeholder", action="store_true", help="Use the ffmpeg placeholder renderer instead")
    args = ap.parse_args()
    public = (args.public_url or f"http://localhost:{args.port}").rstrip("/")

    if not (ROOT / ".env").exists():
        say("runner", "backend/.env not found: copy .env.example and set OPENAI_API_KEY")
        return 1
    stickman_ok = (Path(args.stickman_dir) / "boomdoom_entry.py").exists() and (
        Path(args.stickman_dir) / ".venv" / "bin" / "python"
    ).exists()
    renderer = "stickman" if stickman_ok and not args.placeholder else "placeholder"
    stickman_dir = args.stickman_dir if stickman_ok else ""
    say("runner", f"renderer: {renderer}" + (f" ({stickman_dir})" if stickman_ok else ""))
    DATA.mkdir(exist_ok=True)
    say("runner", "starting embedded PostgreSQL…")
    pg, db_url = start_postgres()

    env = {
        **os.environ,
        **local_jwt_keys(),
        "PYTHONUNBUFFERED": "1",
        "BOOMDOOM_ENV": "local",
        "DATABASE_URL": db_url,
        "QUEUE_BACKEND": "db",
        "STORAGE_BACKEND": "local",
        "LOCAL_STORAGE_DIR": str(DATA / "storage"),
        "RENDER_TMP_DIR": str(DATA / "render"),
        "RENDERER": renderer,
        "STICKMAN_STUDIO_DIR": stickman_dir,
        "FFMPEG_BIN": ffmpeg_path(),
        "CDN_BASE_URL": f"{public}/static",
    }

    say("migrate", "alembic upgrade head")
    mig = subprocess.run([PY, "-m", "alembic", "upgrade", "head"], cwd=ROOT, env=env, capture_output=True, text=True)
    if mig.returncode != 0:
        say("migrate", mig.stderr[-2000:])
        return 1

    commands = {
        "api": [PY, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", str(args.port), "--no-access-log"],
        "script": [PY, "-m", "workers", "script"],
        "render": [PY, "-m", "workers", "render"],
        "scheduler": [PY, "-m", "app.cron", "loop"],
    }
    procs: dict[str, subprocess.Popen] = {}
    for name, cmd in commands.items():
        procs[name] = subprocess.Popen(
            cmd, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
        )
        threading.Thread(target=pump, args=(name, procs[name]), daemon=True).start()

    say("runner", f"API: http://localhost:{args.port}/docs   lessons: {public}/static   (Ctrl-C to stop)")

    stopping = False

    def stop(*_):
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    while not stopping:
        if any(p.poll() is not None for p in procs.values()):
            say("runner", "a process exited; shutting down")
            break
        time.sleep(0.5)

    for p in procs.values():
        if p.poll() is None:
            p.send_signal(signal.SIGTERM)
    deadline = time.time() + 15
    for p in procs.values():
        try:
            p.wait(timeout=max(0.1, deadline - time.time()))
        except subprocess.TimeoutExpired:
            p.kill()
    del pg  # pgserver stops the database when the handle is released
    say("runner", "stopped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
