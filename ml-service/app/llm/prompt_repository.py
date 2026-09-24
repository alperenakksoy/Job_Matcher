import json
import logging

import psycopg
import redis

from app.config import PROMPT_TEMPLATE_CACHE_TTL_SECONDS, REDIS_HOST, REDIS_PORT, database_url

logger = logging.getLogger(__name__)

_redis_client: redis.Redis | None = None


def _get_redis() -> redis.Redis:
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
    return _redis_client


class PromptTemplateNotFoundError(Exception):
    def __init__(self, name: str, provider: str):
        super().__init__(f"No active prompt_template found for name={name!r} provider={provider!r}")


class PromptTemplate:
    __slots__ = ("id", "name", "version", "provider", "prompt")

    def __init__(self, id: str, name: str, version: int, provider: str, prompt: str):
        self.id = id
        self.name = name
        self.version = version
        self.provider = provider
        self.prompt = prompt


def _cache_key(name: str, provider: str) -> str:
    return f"prompt_template:active:{name}:{provider}"


def get_active_prompt_template(name: str, provider: str) -> PromptTemplate:
    """Returns the active prompt_templates row for (name, provider).

    Raises PromptTemplateNotFoundError if none is active - callers should
    treat this as a deployment/seed-data problem, not something to retry.
    """
    cache_key = _cache_key(name, provider)

    try:
        r = _get_redis()
        cached = r.get(cache_key)
        if cached is not None:
            data = json.loads(cached)
            return PromptTemplate(**data)
    except redis.RedisError as e:
        # Redis being down shouldn't take down parsing - fall through to
        # Postgres and just skip caching the result below.
        logger.warning("Redis unavailable when reading prompt template cache: %s", e)

    with psycopg.connect(database_url()) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, name, version, provider, prompt
                FROM prompt_templates
                WHERE name = %s AND provider = %s AND active = true
                """,
                (name, provider),
            )
            row = cur.fetchone()

    if row is None:
        raise PromptTemplateNotFoundError(name, provider)

    template = PromptTemplate(
        id=str(row[0]), name=row[1], version=row[2], provider=row[3], prompt=row[4],
    )

    try:
        r = _get_redis()
        r.setex(
            cache_key,
            PROMPT_TEMPLATE_CACHE_TTL_SECONDS,
            json.dumps({
                "id": template.id, "name": template.name, "version": template.version,
                "provider": template.provider, "prompt": template.prompt,
            }),
        )
    except redis.RedisError as e:
        logger.warning("Failed to cache prompt template in Redis: %s", e)

    return template
