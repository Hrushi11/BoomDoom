"""Runtime configuration.

On AWS every value comes from the ECS task definition: plain settings as `environment`, secrets as
`secrets` (Secrets Manager / SSM, injected as env vars by ECS). Locally a `.env` file is read.
"""

from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# USD per 1M tokens (Product Spec §7.4 item 8). Override with OPENAI_PRICES_JSON if prices change.
DEFAULT_PRICES = {
    "gpt-6.2-sol": [2.00, 10.00],
    "gpt-6.1-terra": [1.00, 5.00],
    "gpt-6-luna": [0.10, 0.50],
}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    boomdoom_env: str = "local"  # local | test | demo | dev | prod
    log_level: str = "INFO"
    service_name: str = "api"

    # ---- database ----
    database_url: str = "postgresql+psycopg://boomdoom:boomdoom@localhost:5432/boomdoom"
    # Alternatively assembled from parts (RDS-managed secret gives username/password separately)
    db_host: str = ""
    db_port: int = 5432
    db_name: str = "boomdoom"
    db_user: str = ""
    db_password: str = ""
    db_sslmode: str = ""  # "require" on AWS
    db_pool_size: int = 5
    db_max_overflow: int = 5

    # ---- queues ----
    # "sqs" on AWS; "db" uses a Postgres-backed queue so local/demo runs need no AWS.
    queue_backend: str = "db"
    aws_region: str = "ap-south-1"
    sqs_script_jobs_url: str = ""
    sqs_render_jobs_url: str = ""
    sqs_deletion_jobs_url: str = ""
    aws_endpoint_url: str = ""  # for LocalStack/moto only

    # ---- storage / CDN ----
    storage_backend: str = "local"  # "s3" on AWS
    local_storage_dir: str = "./.data/storage"
    lessons_bucket: str = "boomdoom-lessons-local"
    cdn_base_url: str = "http://localhost:8000/static"
    cloudfront_key_pair_id: str = ""
    cloudfront_private_key_pem: str = ""  # secret
    signed_url_ttl_s: int = 86_400

    # ---- auth ----
    jwt_private_key_pem: str = ""  # secret
    jwt_public_key_pem: str = ""  # secret
    jwt_key_id: str = "boomdoom-1"
    access_token_ttl_s: int = 3600
    refresh_token_ttl_days: int = 90
    cognito_user_pool_id: str = ""  # P1 optional login
    cognito_app_client_id: str = ""

    # ---- OpenAI ----
    openai_api_key: str = ""  # secret
    openai_model_syllabus: str = "gpt-6.2-sol"
    openai_model_script: str = "gpt-6.2-sol"  # top tier: scene-based scripts drive the animation quality
    openai_model_qa: str = "gpt-6-luna"
    openai_timeout_s: float = 30.0
    openai_daily_cost_cap_usd: float = 20.0
    openai_prices_json: str = ""

    # ---- push ----
    fcm_service_account_json: str = ""  # secret; empty disables push

    # ---- rendering ----
    renderer: str = "placeholder"  # "stickman" = Stickman Studio fork (boomdoom_entry.py)
    stickman_studio_dir: str = ""  # checkout of the fork; empty = renderer unavailable
    stickman_python: str = ""  # its virtualenv's python (default: <dir>/.venv/bin/python)
    stickman_timeout_s: int = 840  # stay under the render-jobs visibility timeout (900 s)
    ffmpeg_bin: str = "ffmpeg"
    render_tmp_dir: str = "/tmp/boomdoom-render"

    # ---- product rules ----
    target_stock: int = 5
    refill_below: int = 3
    syllabus_size: int = 25
    free_lessons_per_day: int = 20
    free_topics_per_day: int = 10
    requests_per_minute: int = 30

    @field_validator("jwt_private_key_pem", "jwt_public_key_pem", "cloudfront_private_key_pem")
    @classmethod
    def _unescape_pem(cls, v: str) -> str:
        """Allow PEMs written on one line with literal \\n (env files, SSM parameters)."""
        return v.replace("\\n", "\n").strip()

    @property
    def is_local(self) -> bool:
        return self.boomdoom_env in {"local", "test"}

    @property
    def sqlalchemy_url(self) -> str:
        if self.db_host:
            from sqlalchemy.engine import URL

            url = URL.create(
                "postgresql+psycopg",
                username=self.db_user,
                password=self.db_password,
                host=self.db_host,
                port=self.db_port,
                database=self.db_name,
                query={"sslmode": self.db_sslmode} if self.db_sslmode else {},
            )
            return url.render_as_string(hide_password=False)
        return self.database_url

    def validate_for_cloud(self) -> list[str]:
        """Settings that must be present outside local/test. Checked at startup (fail fast)."""
        if self.is_local:
            return []
        problems = []
        if not (self.jwt_private_key_pem and self.jwt_public_key_pem):
            problems.append("JWT_PRIVATE_KEY_PEM / JWT_PUBLIC_KEY_PEM")
        if self.queue_backend == "sqs" and not (self.sqs_script_jobs_url and self.sqs_render_jobs_url):
            problems.append("SQS_*_JOBS_URL")
        if bool(self.cloudfront_key_pair_id) != bool(self.cloudfront_private_key_pem):
            problems.append("CLOUDFRONT_KEY_PAIR_ID and CLOUDFRONT_PRIVATE_KEY_PEM must be set together")
        return problems


@lru_cache
def get_settings() -> Settings:
    return Settings()
