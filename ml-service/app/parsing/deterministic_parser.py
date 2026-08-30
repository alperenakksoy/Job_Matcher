"""Deterministic resume parsing: regex for well-structured fields (email,
phone, dates), spaCy for tokenization/section splitting, and a keyword
dictionary for skills. No LLM calls here - this is the "cheap, fast, no
API cost" pass from the plan. Whatever this can't confidently extract is
left as ExtractedField.missing() with source=MISSING, which is exactly
the signal Week 4's LLM completion pass will look for.

Target market is Germany (see plan), so section headings and phone
formats are matched bilingually (German + English).
"""

import re

import spacy

from app.parsing.models import (
    Education,
    ExtractedField,
    ParsedResume,
    WorkExperience,
)
from app.parsing.skills_dictionary import SKILLS

# Loaded once per process - spaCy model load is relatively expensive.
# Falls back to a blank pipeline (tokenizer only, no NER/POS) if the
# language model isn't installed, so the service doesn't crash on startup
# in an environment where `python -m spacy download` wasn't run - it'll
# just have lower quality name detection.
try:
    _nlp = spacy.load("en_core_web_sm")
except OSError:
    _nlp = spacy.blank("en")

EMAIL_PATTERN = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")

# Covers German mobile/landline formats (+49, 0049, leading 0) and generic
# international formats. Intentionally permissive - false positives here
# just mean a low-confidence field gets flagged, not a crash.
PHONE_PATTERN = re.compile(
    r"(\+\d{1,3}[\s/-]?)?"
    r"(\(0\)|0)?"
    r"[\s/-]?\d{2,4}[\s/-]?\d{3,4}[\s/-]?\d{2,4}"
)

SECTION_HEADINGS = {
    "experience": ["experience", "work experience", "berufserfahrung", "erfahrung"],
    "education": ["education", "ausbildung", "studium"],
    "skills": ["skills", "kenntnisse", "fähigkeiten", "kompetenzen"],
}

# "Jan 2023", "January 2023", "01/2023", "2023-01", "2023" - and "Present" /
# "Heute" / "Aktuell" for ongoing roles.
DATE_RANGE_PATTERN = re.compile(
    r"(?P<start>(\w+\s+)?\d{4}|\d{1,2}/\d{4})"
    r"\s*(?:-|–|—|until|bis)\s*"
    r"(?P<end>(\w+\s+)?\d{4}|\d{1,2}/\d{4}|present|heute|aktuell|current)",
    re.IGNORECASE,
)


def parse_resume(raw_text: str) -> ParsedResume:
    result = ParsedResume()

    result.email = _extract_email(raw_text)
    result.phone = _extract_phone(raw_text)
    result.full_name = _extract_name(raw_text)

    sections = _split_into_sections(raw_text)
    result.raw_sections = sections

    if "skills" in sections:
        result.skills = _extract_skills(sections["skills"])
    else:
        # Skills section heading not found - fall back to scanning the
        # whole document, lower confidence since it's less targeted.
        result.skills = _extract_skills(raw_text, confidence=0.5)

    if "experience" in sections:
        result.work_experience = _extract_work_experience(sections["experience"])

    if "education" in sections:
        result.education = _extract_education(sections["education"])

    return result


def _extract_email(text: str) -> ExtractedField:
    match = EMAIL_PATTERN.search(text)
    if not match:
        return ExtractedField.missing()
    return ExtractedField.deterministic(match.group(0), confidence=0.95)


def _extract_phone(text: str) -> ExtractedField:
    for match in PHONE_PATTERN.finditer(text):
        candidate = match.group(0).strip()
        digit_count = sum(c.isdigit() for c in candidate)
        # Require a realistic digit count to avoid matching things like
        # dates or postal codes that happen to fit the loose pattern.
        if digit_count >= 7:
            return ExtractedField.deterministic(candidate, confidence=0.8)
    return ExtractedField.missing()


def _extract_name(text: str) -> ExtractedField:
    # Heuristic: the name is almost always in the first non-empty line of
    # a resume, and short (2-4 tokens). Low-to-medium confidence on purpose
    # - this is one of the fields most likely to need LLM correction.
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        tokens = stripped.split()
        if 1 < len(tokens) <= 4 and all(t[0].isupper() for t in tokens if t[0].isalpha()):
            return ExtractedField.deterministic(stripped, confidence=0.55)
        break  # only ever consider the very first non-empty line
    return ExtractedField.missing()


def _split_into_sections(text: str) -> dict[str, str]:
    """Splits resume text into sections by detecting known headings.

    Returns a dict keyed by our canonical section name (experience/
    education/skills), not the raw heading text found in the document.
    """
    lines = text.splitlines()
    heading_positions: list[tuple[int, str]] = []

    for i, line in enumerate(lines):
        normalized = line.strip().lower().rstrip(":")
        for canonical, variants in SECTION_HEADINGS.items():
            if normalized in variants:
                heading_positions.append((i, canonical))
                break

    sections: dict[str, str] = {}
    for idx, (line_no, canonical) in enumerate(heading_positions):
        start = line_no + 1
        end = heading_positions[idx + 1][0] if idx + 1 < len(heading_positions) else len(lines)
        sections[canonical] = "\n".join(lines[start:end]).strip()

    return sections


def _extract_skills(text: str, confidence: float = 0.75) -> list[ExtractedField]:
    found: list[ExtractedField] = []
    lower_text = text.lower()
    for skill in SKILLS:
        # Word-boundary match so "Go" doesn't match inside "Google".
        if re.search(rf"\b{re.escape(skill.lower())}\b", lower_text):
            found.append(ExtractedField.deterministic(skill, confidence=confidence))
    return found


def _extract_work_experience(section_text: str) -> list[WorkExperience]:
    entries: list[WorkExperience] = []
    lines = [l for l in section_text.splitlines() if l.strip()]

    for i, line in enumerate(lines):
        date_match = DATE_RANGE_PATTERN.search(line)
        if not date_match:
            continue

        entry = WorkExperience()
        entry.start_date = ExtractedField.deterministic(date_match.group("start"), confidence=0.7)
        entry.end_date = ExtractedField.deterministic(date_match.group("end"), confidence=0.7)

        # Naive heuristic: the line itself (minus the date) often contains
        # "Title, Company" or "Company - Title". We don't try to split
        # those reliably here - low confidence, flagged for the LLM pass
        # in Week 4 rather than guessed at with more fragile regex.
        remainder = DATE_RANGE_PATTERN.sub("", line).strip(" ,-–—")
        if remainder:
            entry.title = ExtractedField.deterministic(remainder, confidence=0.4)

        # Description: the next line(s) until the next date range, if any.
        description_lines = []
        for next_line in lines[i + 1:]:
            if DATE_RANGE_PATTERN.search(next_line):
                break
            description_lines.append(next_line)
        if description_lines:
            entry.description = ExtractedField.deterministic(
                " ".join(description_lines).strip(), confidence=0.5
            )

        entries.append(entry)

    return entries


def _extract_education(section_text: str) -> list[Education]:
    entries: list[Education] = []
    lines = [l for l in section_text.splitlines() if l.strip()]

    for line in lines:
        date_match = DATE_RANGE_PATTERN.search(line)
        if not date_match:
            continue

        entry = Education()
        entry.start_date = ExtractedField.deterministic(date_match.group("start"), confidence=0.7)
        entry.end_date = ExtractedField.deterministic(date_match.group("end"), confidence=0.7)

        remainder = DATE_RANGE_PATTERN.sub("", line).strip(" ,-–—")
        if remainder:
            entry.institution = ExtractedField.deterministic(remainder, confidence=0.4)

        entries.append(entry)

    return entries
