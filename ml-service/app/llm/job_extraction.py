"""Orchestrates LLM extraction for job postings.

Simpler than resume_completion.py: there's no "which fields are missing"
check, because there's no deterministic pass to check against - every
call here always goes to the LLM. The plan is explicit about this
asymmetry ("İlanlar için ise LLM ağırlıklı çıkarım (serbest metin)").

A total failure here DOES fail the ParseJob call, unlike resume
completion - a job with no extracted requirements at all isn't a
degraded-but-usable result (there's no deterministic fallback data
sitting underneath it the way there is for a resume), so grpc_server
returns success=False and lets Spring's circuit breaker / retry policy
handle it rather than silently returning an empty ParsedJob.
"""

import logging

from app.llm.execution_logger import log_execution
from app.llm.fallback import AllProvidersFailedError, FallbackLLMProvider
from app.llm.prompt_repository import PromptTemplateNotFoundError, get_active_prompt_template
from app.llm.provider import LLMProviderError
from app.llm.schemas import JobExtraction

logger = logging.getLogger(__name__)

PROMPT_NAME = "job_field_extraction"
PROMPT_PROVIDER = "generic"

# Job descriptions can be long; keep prompts bounded so a huge posting
# doesn't balloon token cost. 8000 chars covers the large majority of
# real postings while capping the outliers.
MAX_DESCRIPTION_CHARS = 8000


class JobExtractionError(Exception):
    """Raised when extraction genuinely fails - caller should surface
    success=False rather than returning an empty/guessed result."""


def extract_job_fields(
    raw_description: str,
    job_id: str,
    fallback_provider: FallbackLLMProvider,
) -> JobExtraction:
    """Returns the LLM-extracted requirements for a job posting.

    Raises JobExtractionError on any failure (no prompt template, all
    providers down, non-retryable provider error) - grpc_server.ParseJob
    catches this and returns a clean success=False response.
    """
    try:
        template = get_active_prompt_template(PROMPT_NAME, PROMPT_PROVIDER)
    except PromptTemplateNotFoundError as e:
        raise JobExtractionError(str(e)) from e

    description = raw_description[:MAX_DESCRIPTION_CHARS]
    prompt = template.prompt.format(job_description=description)

    try:
        result, provider_name = fallback_provider.complete(prompt, JobExtraction)
    except (AllProvidersFailedError, LLMProviderError) as e:
        logger.warning("Job extraction failed for job_id=%s: %s", job_id, e)
        log_execution(
            prompt_template_id=template.id, job_id=job_id,
            provider="none", model="none", input_text=prompt, output_text=None,
            success=False, error_message=str(e), duration_ms=0,
        )
        raise JobExtractionError(str(e)) from e

    extraction: JobExtraction = result.parsed

    log_execution(
        prompt_template_id=template.id, job_id=job_id,
        provider=provider_name, model=result.model,
        input_text=prompt, output_text=extraction.model_dump_json(),
        success=True, prompt_tokens=result.prompt_tokens,
        completion_tokens=result.completion_tokens, duration_ms=result.duration_ms,
    )

    return extraction
