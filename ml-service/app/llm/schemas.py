from pydantic import BaseModel, Field


class EducationCompletion(BaseModel):
    index: int = Field(description="0-based index matching the deterministic parser's education list order")
    institution: str = Field(default="", description="School/university name, empty string if not determinable")
    degree: str = Field(default="", description="e.g. 'M.Sc', 'B.Sc', empty string if not determinable")
    field_of_study: str = Field(default="", description="e.g. 'Computer Science', empty string if not determinable")


class ResumeFieldCompletion(BaseModel):
    """Top-level response shape for the resume field-completion prompt."""

    full_name: str = Field(default="", description="The candidate's full name, empty string if not determinable")
    education: list[EducationCompletion] = Field(default_factory=list)
