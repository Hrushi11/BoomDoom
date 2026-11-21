from fastapi import FastAPI

from app.api import lessons, meta, topics, users
from app.errors import install_handlers


def create_app() -> FastAPI:
    app = FastAPI(title="BoomDoom API", version="1.0.0")
    install_handlers(app)
    app.include_router(meta.router)
    for r in (users.router, topics.router, lessons.router):
        app.include_router(r, prefix="/v1")
    return app


app = create_app()
