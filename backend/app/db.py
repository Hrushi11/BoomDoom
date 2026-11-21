from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import get_settings


class Base(DeclarativeBase):
    pass


_engine: Engine | None = None
_SessionLocal: sessionmaker[Session] | None = None


def get_engine() -> Engine:
    global _engine, _SessionLocal
    if _engine is None:
        s = get_settings()
        _engine = create_engine(
            s.sqlalchemy_url,
            pool_pre_ping=True,
            pool_size=s.db_pool_size,
            max_overflow=s.db_max_overflow,
            pool_recycle=1800,  # RDS / proxies close idle connections
        )
        _SessionLocal = sessionmaker(bind=_engine, expire_on_commit=False)
    return _engine


def reset_engine() -> None:
    """Tests only."""
    global _engine, _SessionLocal
    if _engine is not None:
        _engine.dispose()
    _engine, _SessionLocal = None, None


def get_session() -> Iterator[Session]:
    get_engine()
    assert _SessionLocal is not None
    with _SessionLocal() as session:
        yield session


@contextmanager
def session_scope() -> Iterator[Session]:
    """For workers and cron jobs: commit on success, roll back on error."""
    get_engine()
    assert _SessionLocal is not None
    with _SessionLocal() as session:
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise


def ping() -> bool:
    with get_engine().connect() as conn:
        return conn.execute(text("select 1")).scalar() == 1


def advisory_lock(db: Session, key: str) -> None:
    """Transaction-scoped lock (released on commit/rollback). Serialises stock decisions per topic/share key."""
    db.execute(text("select pg_advisory_xact_lock(hashtext(:k))"), {"k": key})
