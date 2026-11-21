import logging
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.staticfiles import StaticFiles

from app.api import lessons, meta, topics, users
from app.config import get_settings
from app.errors import install_handlers
from app.logs import request_id, setup_logging

log = logging.getLogger("access")


@asynccontextmanager
async def lifespan(_: FastAPI):
    s = get_settings()
    missing = s.validate_for_cloud()
    if missing:
        raise RuntimeError(f"Missing required configuration for {s.boomdoom_env}: {', '.join(missing)}")
    yield


def create_app() -> FastAPI:
    s = get_settings()
    setup_logging(s.service_name, s.log_level)
    app = FastAPI(
        title="BoomDoom API",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs" if s.boomdoom_env != "prod" else None,
        redoc_url=None,
    )
    install_handlers(app)

    @app.middleware("http")
    async def _request_context(request: Request, call_next):
        rid = request.headers.get("x-amzn-trace-id") or request.headers.get("x-request-id") or uuid.uuid4().hex
        token = request_id.set(rid)
        start = time.perf_counter()
        try:
            response = await call_next(request)
        finally:
            request_id.reset(token)
        ms = round((time.perf_counter() - start) * 1000, 1)
        if request.url.path not in ("/healthz", "/readyz"):
            # Path templates, not raw paths, so IDs don't end up in logs.
            route = request.scope.get("route")
            log.info(
                "request",
                extra={
                    "method": request.method,
                    "path": getattr(route, "path", "?"),
                    "status": response.status_code,
                    "duration_ms": ms,
                },
            )
        response.headers["x-request-id"] = rid
        return response

    app.include_router(meta.router)
    for r in (users.router, topics.router, lessons.router):
        app.include_router(r, prefix="/v1")

    if s.storage_backend == "local":  # serve lesson files locally; CloudFront does this on AWS
        root = Path(s.local_storage_dir)
        root.mkdir(parents=True, exist_ok=True)
        app.mount("/static", StaticFiles(directory=root), name="static")
    return app


app = create_app()
