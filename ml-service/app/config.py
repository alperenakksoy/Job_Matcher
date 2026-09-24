import os

# Same Postgres instance Spring's Flyway owns (see V4__prompt_templates.sql).
# DB_HOST defaults to localhost for local dev (uvicorn running outside
# Docker); becomes the compose service name once ml-service joins
# docker-compose.yml.
DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = int(os.environ.get("DB_PORT", "5432"))
DB_NAME = os.environ.get("POSTGRES_DB", "jobmatcher")
DB_USER = os.environ.get("POSTGRES_USER", "jobmatcher")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "")

REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

# How long a fetched prompt_templates row stays cached in Redis before
# re-querying Postgres. Short enough that flipping a template's `active`
# flag takes effect quickly; long enough to avoid hitting Postgres on
# every single resume parsed.
PROMPT_TEMPLATE_CACHE_TTL_SECONDS = int(os.environ.get("PROMPT_TEMPLATE_CACHE_TTL_SECONDS", "60"))


def database_url() -> str:
    return f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
