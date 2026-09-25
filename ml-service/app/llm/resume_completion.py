import logging

from app.llm.execution_logger import log_execution
from app.llm.fallback import AllProvidersFailedError, FallbackLLMProvider
from app.llm.prompt_repository import PromptTemplateNotFoundError, get_active_prompt_template
from app.llm.provider import LLMProviderError
from app.llm.schemas import ResumeFieldCompletion
from app.parsing import models

logger = logging.getLogger(__name__)

PROMPT_NAME = "resume_field_completion"
PROMPT_PROVIDER = "generic"  # prompt text itself is provider-agnostic; see prompt_repository docstring


def _needs_completion(parsed: models.ParsedResume) -> bool:
    if parsed.full_name.source == models.ExtractionSource.MISSING:
        return True
    return any(
        edu.institution.source == models.ExtractionSource.MISSING
        or edu.degree.source == models.ExtractionSource.MISSING
        or edu.field_of_study.source == models.ExtractionSource.MISSING
        for edu in parsed.education
    )


def _build_prompt(template: str, parsed: models.ParsedResume, raw_text: str) -> str:
    # Best-effort raw context for full-name extraction: the top of the
    # document is where a CV's name almost always lives, and a few hundred
    # chars is enough context without ballooning token cost.
    header_snippet = raw_text[:1500]
    education_section = parsed_education_raw_text(parsed)

    return template.format(
        header_snippet=header_snippet,
        education_section=education_section,
        education_count=len(parsed.education),
    )


def parsed_education_raw_text(parsed: models.ParsedResume) -> str:
    # The deterministic parser already segmented education entries out of
    # raw_sections["education"] - reuse that same raw text rather than
    # reconstructing it from the (partially empty) structured fields, so
    # the LLM sees the original wording.
    return parsed.raw_sections.get("education", "")


def complete_missing_fields(
        parsed: models.ParsedResume,
        raw_text: str,
        resume_id: str,
        fallback_provider: FallbackLLMProvider,
) -> models.ParsedResume:
    """Returns a ParsedResume with LLM-completed values merged in.

    Mutates and returns the same object's fields where completion
    succeeds; leaves everything else untouched. Never raises - any
    failure here means the fields stay MISSING, logged as a warning.
    """
    if not _needs_completion(parsed):
        return parsed

    try:
        template = get_active_prompt_template(PROMPT_NAME, PROMPT_PROVIDER)
    except PromptTemplateNotFoundError as e:
        logger.error("Cannot complete missing fields: %s", e)
        return parsed

    prompt = _build_prompt(template.prompt, parsed, raw_text)

    try:
        result, provider_name = fallback_provider.complete(prompt, ResumeFieldCompletion)
    except (AllProvidersFailedError, LLMProviderError) as e:
        # AllProvidersFailedError: every provider failed with a retryable
        # error. LLMProviderError: some provider (possibly the last one in
        # the chain) failed with a NON-retryable error, which
        # FallbackLLMProvider re-raises immediately instead of wrapping -
        # see fallback.py. Either way, missing fields just stay MISSING;
        # this never fails the overall resume parse.
        logger.warning("LLM completion failed for resume_id=%s: %s", resume_id, e)
        log_execution(
            prompt_template_id=template.id, resume_id=resume_id,
            provider="none", model="none", input_text=prompt, output_text=None,
            success=False, error_message=str(e), duration_ms=0,
        )
        return parsed

    completion: ResumeFieldCompletion = result.parsed

    log_execution(
        prompt_template_id=template.id, resume_id=resume_id,
        provider=provider_name, model=result.model,
        input_text=prompt, output_text=completion.model_dump_json(),
        success=True, prompt_tokens=result.prompt_tokens,
        completion_tokens=result.completion_tokens, duration_ms=result.duration_ms,
    )

    if completion.full_name and parsed.full_name.source == models.ExtractionSource.MISSING:
        parsed.full_name = models.ExtractedField(
            value=completion.full_name, confidence=0.7, source=models.ExtractionSource.LLM,
        )

    completions_by_index = {c.index: c for c in completion.education}
    for i, edu in enumerate(parsed.education):
        edu_completion = completions_by_index.get(i)
        if edu_completion is None:
            continue
        if edu_completion.institution and edu.institution.source == models.ExtractionSource.MISSING:
            edu.institution = models.ExtractedField(
                value=edu_completion.institution, confidence=0.7, source=models.ExtractionSource.LLM,
            )
        if edu_completion.degree and edu.degree.source == models.ExtractionSource.MISSING:
            edu.degree = models.ExtractedField(
                value=edu_completion.degree, confidence=0.7, source=models.ExtractionSource.LLM,
            )
        if edu_completion.field_of_study and edu.field_of_study.source == models.ExtractionSource.MISSING:
            edu.field_of_study = models.ExtractedField(
                value=edu_completion.field_of_study, confidence=0.7, source=models.ExtractionSource.LLM,
            )

    return parsed