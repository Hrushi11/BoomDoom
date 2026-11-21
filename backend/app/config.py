"""Runtime configuration. Values come from env vars (prefix-free) or a local .env file."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    boomdoom_env: str = "local"
    database_url: str = "postgresql+psycopg://boomdoom:boomdoom@localhost:5432/boomdoom"

    aws_region: str = "ap-south-1"
    sqs_script_jobs_url: str = ""
    sqs_render_jobs_url: str = ""
    sqs_deletion_jobs_url: str = ""
    lessons_bucket: str = "boomdoom-lessons-local"
    cdn_base_url: str = "http://localhost:8000/static"

    jwt_private_key_pem: str = ""
    jwt_public_key_pem: str = ""
    access_token_ttl_s: int = 3600
    refresh_token_ttl_days: int = 90

    openai_api_key: str = ""
    openai_model_syllabus: str = "gpt-6.2-sol"
    openai_model_script: str = "gpt-6.1-terra"
    openai_model_qa: str = "gpt-6-luna"
    openai_daily_cost_cap_usd: float = 20.0

    # §8.6 stock targets
    target_stock: int = 5
    refill_below: int = 3

    @property
    def is_local(self) -> bool:
        return self.boomdoom_env in {"local", "test"}


@lru_cache
def get_settings() -> Settings:
    return Settings()
