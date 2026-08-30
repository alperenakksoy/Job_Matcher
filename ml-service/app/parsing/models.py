"""Plain-Python mirror of the proto ExtractedField message.

Parsing logic in this package works with these dataclasses, not the
generated protobuf classes directly - keeps the extraction logic testable
without needing the generated stubs, and keeps the proto <-> Python
mapping in exactly one place (grpc_server.py).
"""

from dataclasses import dataclass, field
from enum import Enum


class ExtractionSource(str, Enum):
    DETERMINISTIC = "DETERMINISTIC"
    LLM = "LLM"  # not produced by this week's deterministic parser
    MISSING = "MISSING"


@dataclass
class ExtractedField:
    value: str
    confidence: float
    source: ExtractionSource

    @staticmethod
    def missing() -> "ExtractedField":
        return ExtractedField(value="", confidence=0.0, source=ExtractionSource.MISSING)

    @staticmethod
    def deterministic(value: str, confidence: float) -> "ExtractedField":
        return ExtractedField(value=value, confidence=confidence, source=ExtractionSource.DETERMINISTIC)


@dataclass
class WorkExperience:
    company: ExtractedField = field(default_factory=ExtractedField.missing)
    title: ExtractedField = field(default_factory=ExtractedField.missing)
    start_date: ExtractedField = field(default_factory=ExtractedField.missing)
    end_date: ExtractedField = field(default_factory=ExtractedField.missing)
    description: ExtractedField = field(default_factory=ExtractedField.missing)


@dataclass
class Education:
    institution: ExtractedField = field(default_factory=ExtractedField.missing)
    degree: ExtractedField = field(default_factory=ExtractedField.missing)
    field_of_study: ExtractedField = field(default_factory=ExtractedField.missing)
    start_date: ExtractedField = field(default_factory=ExtractedField.missing)
    end_date: ExtractedField = field(default_factory=ExtractedField.missing)


@dataclass
class ParsedResume:
    email: ExtractedField = field(default_factory=ExtractedField.missing)
    phone: ExtractedField = field(default_factory=ExtractedField.missing)
    full_name: ExtractedField = field(default_factory=ExtractedField.missing)
    work_experience: list[WorkExperience] = field(default_factory=list)
    education: list[Education] = field(default_factory=list)
    skills: list[ExtractedField] = field(default_factory=list)
    raw_sections: dict[str, str] = field(default_factory=dict)
