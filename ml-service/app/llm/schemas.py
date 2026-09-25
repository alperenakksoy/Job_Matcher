"""Pydantic schemas for LLM-based field completion.

These are the *response* shapes instructor forces the LLM's output into -
not the same as app.parsing.models (which carries confidence/source
metadata the LLM never produces). A mapping step in resume_completion.py
converts these into ExtractedField(source=LLM) values.
"""

from pydantic import BaseModel, Field


class EducationCompletion(BaseModel):
    """One education entry's LLM-completed fields.

    index must match the position of the corresponding entry in the
    deterministic parser's education list - the LLM is given the raw
    education section text plus how many entries the deterministic parser
    already split it into, and asked to fill in the same number of entries
    in the same order, rather than re-segmenting the text itself. Keeping
    segmentation deterministic and only delegating field extraction to the
    LLM keeps this cheap and avoids the LLM inventing or merging entries.
    """

    index: int = Field(description="0-based index matching the deterministic parser's education list order")
    institution: str = Field(default="", description="School/university name, empty string if not determinable")
    degree: str = Field(default="", description="e.g. 'M.Sc', 'B.Sc', empty string if not determinable")
    field_of_study: str = Field(default="", description="e.g. 'Computer Science', empty string if not determinable")


class ResumeFieldCompletion(BaseModel):
    """Top-level response shape for the resume field-completion prompt."""

    full_name: str = Field(default="", description="The candidate's full name, empty string if not determinable")
    education: list[EducationCompletion] = Field(default_factory=list)


class JobExtraction(BaseModel):
    """Top-level response shape for job-posting extraction.

    Unlike resume completion, this is the LLM's entire job for a posting -
    there's no deterministic pre-pass (per the plan: job postings are
    free text with no reliable structural markers to regex against, unlike
    a resume's fairly consistent section headers). So every field here is
    always LLM-sourced when success=True, never MISSING/DETERMINISTIC.
    """

    required_skills: list[str] = Field(
        default_factory=list,
        description="Skills explicitly required (not just nice-to-have) for this role",
    )
    nice_to_have_skills: list[str] = Field(
        default_factory=list,
        description="Skills mentioned as a plus/bonus/nice-to-have rather than required",
    )
    seniority_level: str = Field(
        default="",
        description="One of: intern, junior, mid, senior, lead, principal - empty string if not determinable",
    )
    employment_type: str = Field(
        default="",
        description="One of: full_time, part_time, contract, internship - empty string if not determinable",
    )