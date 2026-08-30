"""gRPC service implementation. This is the only place that translates
between our plain-Python dataclasses (app.parsing.models) and the
generated protobuf message classes - keeps that mapping in one spot
instead of scattered across the parsing logic.
"""

import logging

import grpc

from app.generated import job_matcher_pb2 as pb2
from app.generated import job_matcher_pb2_grpc as pb2_grpc
from app.parsing import models
from app.parsing.deterministic_parser import parse_resume
from app.parsing.pdf_extractor import PdfExtractionError, extract_text

logger = logging.getLogger(__name__)

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

        return pb2.ParseResumeResponse(
            resume_id=request.resume_id,
            success=True,
            parsed=_parsed_resume_to_proto(parsed),
        )

    def ParseJob(self, request: pb2.ParseJobRequest, context) -> pb2.ParseJobResponse:
        # Skeleton only - implemented in Week 4 (LLM-heavy extraction for
        # free-text job postings, per the plan). Returning a clear
        # success=False here rather than an empty/zero-value response so
        # Spring's Resilience4j circuit breaker and the caller's own
        # error handling have something explicit to act on.
        logger.info("ParseJob called for job_id=%s - not implemented until Week 4", request.job_id)
        return pb2.ParseJobResponse(
            job_id=request.job_id,
            success=False,
            error_message="ParseJob is not implemented yet (planned for Week 4)",
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
