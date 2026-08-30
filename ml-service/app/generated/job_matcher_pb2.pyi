from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ExtractionSource(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    EXTRACTION_SOURCE_UNSPECIFIED: _ClassVar[ExtractionSource]
    DETERMINISTIC: _ClassVar[ExtractionSource]
    LLM: _ClassVar[ExtractionSource]
    MISSING: _ClassVar[ExtractionSource]
EXTRACTION_SOURCE_UNSPECIFIED: ExtractionSource
DETERMINISTIC: ExtractionSource
LLM: ExtractionSource
MISSING: ExtractionSource

class ExtractedField(_message.Message):
    __slots__ = ("value", "confidence", "source")
    VALUE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    value: str
    confidence: float
    source: ExtractionSource
    def __init__(self, value: _Optional[str] = ..., confidence: _Optional[float] = ..., source: _Optional[_Union[ExtractionSource, str]] = ...) -> None: ...

class ExtractedFieldList(_message.Message):
    __slots__ = ("values",)
    VALUES_FIELD_NUMBER: _ClassVar[int]
    values: _containers.RepeatedCompositeFieldContainer[ExtractedField]
    def __init__(self, values: _Optional[_Iterable[_Union[ExtractedField, _Mapping]]] = ...) -> None: ...

class ParseResumeRequest(_message.Message):
    __slots__ = ("resume_id", "file_content", "original_filename")
    RESUME_ID_FIELD_NUMBER: _ClassVar[int]
    FILE_CONTENT_FIELD_NUMBER: _ClassVar[int]
    ORIGINAL_FILENAME_FIELD_NUMBER: _ClassVar[int]
    resume_id: str
    file_content: bytes
    original_filename: str
    def __init__(self, resume_id: _Optional[str] = ..., file_content: _Optional[bytes] = ..., original_filename: _Optional[str] = ...) -> None: ...

class ParsedResume(_message.Message):
    __slots__ = ("email", "phone", "full_name", "work_experience", "education", "skills", "raw_sections")
    class RawSectionsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    PHONE_FIELD_NUMBER: _ClassVar[int]
    FULL_NAME_FIELD_NUMBER: _ClassVar[int]
    WORK_EXPERIENCE_FIELD_NUMBER: _ClassVar[int]
    EDUCATION_FIELD_NUMBER: _ClassVar[int]
    SKILLS_FIELD_NUMBER: _ClassVar[int]
    RAW_SECTIONS_FIELD_NUMBER: _ClassVar[int]
    email: ExtractedField
    phone: ExtractedField
    full_name: ExtractedField
    work_experience: _containers.RepeatedCompositeFieldContainer[WorkExperience]
    education: _containers.RepeatedCompositeFieldContainer[Education]
    skills: ExtractedFieldList
    raw_sections: _containers.ScalarMap[str, str]
    def __init__(self, email: _Optional[_Union[ExtractedField, _Mapping]] = ..., phone: _Optional[_Union[ExtractedField, _Mapping]] = ..., full_name: _Optional[_Union[ExtractedField, _Mapping]] = ..., work_experience: _Optional[_Iterable[_Union[WorkExperience, _Mapping]]] = ..., education: _Optional[_Iterable[_Union[Education, _Mapping]]] = ..., skills: _Optional[_Union[ExtractedFieldList, _Mapping]] = ..., raw_sections: _Optional[_Mapping[str, str]] = ...) -> None: ...

class WorkExperience(_message.Message):
    __slots__ = ("company", "title", "start_date", "end_date", "description")
    COMPANY_FIELD_NUMBER: _ClassVar[int]
    TITLE_FIELD_NUMBER: _ClassVar[int]
    START_DATE_FIELD_NUMBER: _ClassVar[int]
    END_DATE_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    company: ExtractedField
    title: ExtractedField
    start_date: ExtractedField
    end_date: ExtractedField
    description: ExtractedField
    def __init__(self, company: _Optional[_Union[ExtractedField, _Mapping]] = ..., title: _Optional[_Union[ExtractedField, _Mapping]] = ..., start_date: _Optional[_Union[ExtractedField, _Mapping]] = ..., end_date: _Optional[_Union[ExtractedField, _Mapping]] = ..., description: _Optional[_Union[ExtractedField, _Mapping]] = ...) -> None: ...

class Education(_message.Message):
    __slots__ = ("institution", "degree", "field_of_study", "start_date", "end_date")
    INSTITUTION_FIELD_NUMBER: _ClassVar[int]
    DEGREE_FIELD_NUMBER: _ClassVar[int]
    FIELD_OF_STUDY_FIELD_NUMBER: _ClassVar[int]
    START_DATE_FIELD_NUMBER: _ClassVar[int]
    END_DATE_FIELD_NUMBER: _ClassVar[int]
    institution: ExtractedField
    degree: ExtractedField
    field_of_study: ExtractedField
    start_date: ExtractedField
    end_date: ExtractedField
    def __init__(self, institution: _Optional[_Union[ExtractedField, _Mapping]] = ..., degree: _Optional[_Union[ExtractedField, _Mapping]] = ..., field_of_study: _Optional[_Union[ExtractedField, _Mapping]] = ..., start_date: _Optional[_Union[ExtractedField, _Mapping]] = ..., end_date: _Optional[_Union[ExtractedField, _Mapping]] = ...) -> None: ...

class ParseResumeResponse(_message.Message):
    __slots__ = ("resume_id", "success", "error_message", "parsed")
    RESUME_ID_FIELD_NUMBER: _ClassVar[int]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    PARSED_FIELD_NUMBER: _ClassVar[int]
    resume_id: str
    success: bool
    error_message: str
    parsed: ParsedResume
    def __init__(self, resume_id: _Optional[str] = ..., success: _Optional[bool] = ..., error_message: _Optional[str] = ..., parsed: _Optional[_Union[ParsedResume, _Mapping]] = ...) -> None: ...

class ParseJobRequest(_message.Message):
    __slots__ = ("job_id", "raw_description")
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    RAW_DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    job_id: str
    raw_description: str
    def __init__(self, job_id: _Optional[str] = ..., raw_description: _Optional[str] = ...) -> None: ...

class ParsedJob(_message.Message):
    __slots__ = ("required_skills", "nice_to_have_skills", "seniority_level", "employment_type")
    REQUIRED_SKILLS_FIELD_NUMBER: _ClassVar[int]
    NICE_TO_HAVE_SKILLS_FIELD_NUMBER: _ClassVar[int]
    SENIORITY_LEVEL_FIELD_NUMBER: _ClassVar[int]
    EMPLOYMENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    required_skills: ExtractedFieldList
    nice_to_have_skills: ExtractedFieldList
    seniority_level: ExtractedField
    employment_type: ExtractedField
    def __init__(self, required_skills: _Optional[_Union[ExtractedFieldList, _Mapping]] = ..., nice_to_have_skills: _Optional[_Union[ExtractedFieldList, _Mapping]] = ..., seniority_level: _Optional[_Union[ExtractedField, _Mapping]] = ..., employment_type: _Optional[_Union[ExtractedField, _Mapping]] = ...) -> None: ...

class ParseJobResponse(_message.Message):
    __slots__ = ("job_id", "success", "error_message", "parsed")
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    PARSED_FIELD_NUMBER: _ClassVar[int]
    job_id: str
    success: bool
    error_message: str
    parsed: ParsedJob
    def __init__(self, job_id: _Optional[str] = ..., success: _Optional[bool] = ..., error_message: _Optional[str] = ..., parsed: _Optional[_Union[ParsedJob, _Mapping]] = ...) -> None: ...

class ComputeMatchRequest(_message.Message):
    __slots__ = ("resume_id", "job_id")
    RESUME_ID_FIELD_NUMBER: _ClassVar[int]
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    resume_id: str
    job_id: str
    def __init__(self, resume_id: _Optional[str] = ..., job_id: _Optional[str] = ...) -> None: ...

class MatchScoreBreakdown(_message.Message):
    __slots__ = ("semantic_score", "skills_score", "experience_score", "language_score", "total_score")
    SEMANTIC_SCORE_FIELD_NUMBER: _ClassVar[int]
    SKILLS_SCORE_FIELD_NUMBER: _ClassVar[int]
    EXPERIENCE_SCORE_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_SCORE_FIELD_NUMBER: _ClassVar[int]
    TOTAL_SCORE_FIELD_NUMBER: _ClassVar[int]
    semantic_score: float
    skills_score: float
    experience_score: float
    language_score: float
    total_score: float
    def __init__(self, semantic_score: _Optional[float] = ..., skills_score: _Optional[float] = ..., experience_score: _Optional[float] = ..., language_score: _Optional[float] = ..., total_score: _Optional[float] = ...) -> None: ...

class ComputeMatchResponse(_message.Message):
    __slots__ = ("resume_id", "job_id", "success", "error_message", "breakdown")
    RESUME_ID_FIELD_NUMBER: _ClassVar[int]
    JOB_ID_FIELD_NUMBER: _ClassVar[int]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    BREAKDOWN_FIELD_NUMBER: _ClassVar[int]
    resume_id: str
    job_id: str
    success: bool
    error_message: str
    breakdown: MatchScoreBreakdown
    def __init__(self, resume_id: _Optional[str] = ..., job_id: _Optional[str] = ..., success: _Optional[bool] = ..., error_message: _Optional[str] = ..., breakdown: _Optional[_Union[MatchScoreBreakdown, _Mapping]] = ...) -> None: ...
