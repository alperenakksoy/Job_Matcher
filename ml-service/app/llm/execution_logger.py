"""Logs every LLM call to prompt_executions - the raw material for the
Week 9 eval comparisons (deterministic vs +LLM, prompt v1 vs v2 vs v3).

Logging failures never propagate: a broken logging path shouldn't turn a
successful resume parse into a failed one, or mask the original LLM error
with a logging error.
"""

import logging

import psycopg

from app.config import database_url

logger = logging.getLogger(__name__)


def log_execution(
        *,
        prompt_template_id: str | None,
        resume_id: str | None = None,
        job_id: str | None = None,
        provider: str,
        model: str,
        input_text: str,
        output_text: str | None,
        success: bool,
        error_message: str | None = None,
        prompt_tokens: int | None = None,
        completion_tokens: int | None = None,
        duration_ms: int,
        estimated_cost_usd: float | None = None,
) -> None:
    try:
        with psycopg.connect(database_url()) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO prompt_executions (
                        prompt_template_id, resume_id, job_id, provider, model,
                        input, output, success, error_message,
                        prompt_tokens, completion_tokens, duration_ms, estimated_cost_usd
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        prompt_template_id, resume_id, job_id, provider, model,
                        input_text, output_text, success, error_message,
                        prompt_tokens, completion_tokens, duration_ms, estimated_cost_usd,
                    ),
                )
            conn.commit()
    except Exception:
        # Best-effort logging - see module docstring for why this never raises.
        logger.exception(
            "Failed to write prompt_executions row (resume_id=%s, job_id=%s, provider=%s)",
            resume_id, job_id, provider,
        )