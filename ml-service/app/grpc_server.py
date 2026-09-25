"""gRPC service implementation. This is the only place that translates
between our plain-Python dataclasses (app.parsing.models) and the
generated protobuf message classes - keeps that mapping in one spot
instead of scattered across the parsing logic.
"""

import logging

import grpc

from app.generated import job_matcher_pb2 as pb2
from app.generated import job_matcher_pb2_grpc as pb2_grpc
from app.llm.fallback import FallbackLLMProvider
from app.llm.job_extraction import JobExtractionError, extract_job_fields
from app.llm.providers import GeminiProvider, GroqProvider
from app.llm.resume_completion import complete_missing_fields
from app.llm.schemas import JobExtraction
from app.parsing import models
from app.parsing.deterministic_parser import parse_resume
from app.parsing.pdf_extractor import PdfExtractionError, extract_text

logger = logging.getLogger(__name__)

# Built once per process, not per request - each provider's underlying
# instructor/SDK client does its own connection pooling internally.
# Order matters: Gemini first (generous free tier for this project's
# volume), Groq as fallback.
_fallback_provider = FallbackLLMProvider([GeminiProvider(), GroqProvider()])

_SOURCE_TO_PROTO = {
    models.ExtractionSource.DETERMINISTIC: pb2.DETERMINISTIC,
    models.ExtractionSource.LLM: pb2.LLM,
    models.ExtractionSource.MISSING: pb2.MISSING,
}


def _field_to_proto(f: models.ExtractedField) -> pb2.ExtractedField:
    return pb2.ExtractedField(
        value=f.value,
        confidence=f.confidence,
        source=_SOURCE_TO_PROTO[f.source],
    )


def _work_experience_to_proto(w: models.WorkExperience) -> pb2.WorkExperience:
    return pb2.WorkExperience(
        company=_field_to_proto(w.company),
        title=_field_to_proto(w.title),
        start_date=_field_to_proto(w.start_date),
        end_date=_field_to_proto(w.end_date),
        description=_field_to_proto(w.description),
    )


def _education_to_proto(e: models.Education) -> pb2.Education:
    return pb2.Education(
        institution=_field_to_proto(e.institution),
        degree=_field_to_proto(e.degree),
        field_of_study=_field_to_proto(e.field_of_study),
        start_date=_field_to_proto(e.start_date),
        end_date=_field_to_proto(e.end_date),
    )


def _parsed_resume_to_proto(parsed: models.ParsedResume) -> pb2.ParsedResume:
    return pb2.ParsedResume(
        email=_field_to_proto(parsed.email),
        phone=_field_to_proto(parsed.phone),
        full_name=_field_to_proto(parsed.full_name),
        work_experience=[_work_experience_to_proto(w) for w in parsed.work_experience],
        education=[_education_to_proto(e) for e in parsed.education],
        skills=pb2.ExtractedFieldList(values=[_field_to_proto(s) for s in parsed.skills]),
        raw_sections=parsed.raw_sections,
    )


# Confidence for job extraction is a flat constant rather than something
# the LLM reports per-field: instructor forces the response into
# JobExtraction, which has no per-field confidence score, and asking the
# LLM to self-report confidence values is unreliable in practice. 0.8
# reflects "the LLM extracted this from explicit text" without pretending
# to a precision the model doesn't actually have.
_JOB_EXTRACTION_CONFIDENCE = 0.8


def _job_skill_to_proto(skill: str) -> pb2.ExtractedField:
    return pb2.ExtractedField(
        value=skill, confidence=_JOB_EXTRACTION_CONFIDENCE, source=pb2.LLM,
    )


def _job_single_field_to_proto(value: str) -> pb2.ExtractedField:
    if not value:
        return pb2.ExtractedField(value="", confidence=0.0, source=pb2.MISSING)
    return pb2.ExtractedField(value=value, confidence=_JOB_EXTRACTION_CONFIDENCE, source=pb2.LLM)


def _job_extraction_to_proto(extraction: JobExtraction) -> pb2.ParsedJob:
    return pb2.ParsedJob(
        required_skills=pb2.ExtractedFieldList(
            values=[_job_skill_to_proto(s) for s in extraction.required_skills],
        ),
        nice_to_have_skills=pb2.ExtractedFieldList(
            values=[_job_skill_to_proto(s) for s in extraction.nice_to_have_skills],
        ),
        seniority_level=_job_single_field_to_proto(extraction.seniority_level),
        employment_type=_job_single_field_to_proto(extraction.employment_type),
    )


class JobMatcherMlServicer(pb2_grpc.JobMatcherMlServiceServicer):

    def ParseResume(self, request: pb2.ParseResumeRequest, context) -> pb2.ParseResumeResponse:
        logger.info(
            "ParseResume request received: resume_id=%s filename=%s size=%d bytes",
            request.resume_id, request.original_filename, len(request.file_content),
        )

        try:
            raw_text = extract_text(request.file_content)
        except PdfExtractionError as e:
            logger.warning("PDF extraction failed for resume_id=%s: %s", request.resume_id, e)
            return pb2.ParseResumeResponse(
                resume_id=request.resume_id,
                success=False,
                error_message=str(e),
            )

        try:
            parsed = parse_resume(raw_text)
        except Exception as e:
            # Deterministic parsing should never throw in normal operation
            # (it's all regex/dict lookups over already-extracted text),
            # but if it does, fail the request cleanly rather than crashing
            # the whole gRPC server process.
            logger.exception("Unexpected error parsing resume_id=%s", request.resume_id)
            return pb2.ParseResumeResponse(
                resume_id=request.resume_id,
                success=False,
                error_message=f"Internal parsing error: {e}",
            )

        # LLM completion never fails the request - see resume_completion's
        # module docstring. Any missing fields it can't fill just stay
        # MISSING, exactly as the deterministic parser left them.
        parsed = complete_missing_fields(
            parsed, raw_text, request.resume_id, _fallback_provider,
        )

        return pb2.ParseResumeResponse(
            resume_id=request.resume_id,
            success=True,
            parsed=_parsed_resume_to_proto(parsed),
        )

    def ParseJob(self, request: pb2.ParseJobRequest, context) -> pb2.ParseJobResponse:
        logger.info(
            "ParseJob request received: job_id=%s description_length=%d",
            request.job_id, len(request.raw_description),
        )

        if not request.raw_description.strip():
            return pb2.ParseJobResponse(
                job_id=request.job_id,
                success=False,
                error_message="raw_description is empty",
            )

        try:
            extraction = extract_job_fields(
                request.raw_description, request.job_id, _fallback_provider,
            )
        except JobExtractionError as e:
            # Unlike resume parsing, there's no deterministic fallback data
            # underneath a failed job extraction - an empty ParsedJob would
            # look like "this posting genuinely has no requirements" rather
            # than "extraction failed", which is a meaningfully different
            # and worse signal to feed into matching later. So this fails
            # the whole request instead of returning a degraded result.
            logger.warning("Job extraction failed for job_id=%s: %s", request.job_id, e)
            return pb2.ParseJobResponse(
                job_id=request.job_id,
                success=False,
                error_message=str(e),
            )

        return pb2.ParseJobResponse(
            job_id=request.job_id,
            success=True,
            parsed=_job_extraction_to_proto(extraction),
        )

    def ComputeMatch(self, request: pb2.ComputeMatchRequest, context) -> pb2.ComputeMatchResponse:
        # Skeleton only - implemented in Week 5 (embeddings + weighted
        # scoring, per the plan).
        logger.info(
            "ComputeMatch called for resume_id=%s job_id=%s - not implemented until Week 5",
            request.resume_id, request.job_id,
        )
        return pb2.ComputeMatchResponse(
            resume_id=request.resume_id,
            job_id=request.job_id,
            success=False,
            error_message="ComputeMatch is not implemented yet (planned for Week 5)",
        )