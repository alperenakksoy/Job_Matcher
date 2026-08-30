import pymupdf
import pytest

from app.parsing.deterministic_parser import parse_resume
from app.parsing.models import ExtractionSource
from app.parsing.pdf_extractor import PdfExtractionError, extract_text


def make_pdf_bytes(text: str) -> bytes:
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((50, 50), text, fontsize=11)
    return doc.tobytes()


SAMPLE_TEXT = """Alperen Aksoy
alperen.aksoy@example.com
+49 176 12345678

Experience

Jan 2022 - Present
Backend Engineer at Acme GmbH
Built scalable microservices using Java, Spring Boot, PostgreSQL and Docker.

Education

Oct 2018 - Sep 2021
Technical University Munich

Skills

Java, Python, Spring Boot, Docker, Kubernetes, PostgreSQL, React
"""


class TestPdfExtraction:
    def test_extracts_text_from_valid_pdf(self):
        pdf_bytes = make_pdf_bytes(SAMPLE_TEXT)
        text = extract_text(pdf_bytes)
        assert "alperen.aksoy@example.com" in text

    def test_rejects_non_pdf_bytes(self):
        with pytest.raises(PdfExtractionError):
            extract_text(b"not a pdf")

    def test_rejects_empty_bytes(self):
        with pytest.raises(PdfExtractionError):
            extract_text(b"")


class TestDeterministicParser:
    @pytest.fixture
    def parsed(self):
        return parse_resume(SAMPLE_TEXT)

    def test_extracts_email(self, parsed):
        assert parsed.email.value == "alperen.aksoy@example.com"
        assert parsed.email.source == ExtractionSource.DETERMINISTIC
        assert parsed.email.confidence > 0.9

    def test_extracts_phone(self, parsed):
        assert "176" in parsed.phone.value
        assert parsed.phone.source == ExtractionSource.DETERMINISTIC

    def test_extracts_name_from_first_line(self, parsed):
        assert parsed.full_name.value == "Alperen Aksoy"

    def test_missing_field_has_zero_confidence_and_missing_source(self):
        parsed = parse_resume("no contact info here at all, just plain text")
        assert parsed.email.source == ExtractionSource.MISSING
        assert parsed.email.confidence == 0.0
        assert parsed.email.value == ""

    def test_extracts_known_skills(self, parsed):
        skill_values = {s.value for s in parsed.skills}
        assert "Java" in skill_values
        assert "Spring Boot" in skill_values
        assert "Docker" in skill_values

    def test_does_not_match_partial_skill_words(self):
        # "Go" should not match inside "Google" - word boundary check
        parsed = parse_resume("I use Google Cloud a lot")
        skill_values = {s.value for s in parsed.skills}
        assert "Go" not in skill_values

    def test_extracts_work_experience_dates(self, parsed):
        assert len(parsed.work_experience) == 1
        exp = parsed.work_experience[0]
        assert exp.start_date.value == "Jan 2022"
        assert exp.end_date.value == "Present"

    def test_extracts_education_dates_full_end_date_not_truncated(self, parsed):
        # Regression test: a earlier regex bug truncated "Sep 2021" to "ep 2021"
        # because the separator was a character class (matching individual
        # letters) instead of a proper alternation - IGNORECASE let 's' from
        # "bis" swallow the leading 'S' of "Sep".
        assert len(parsed.education) == 1
        edu = parsed.education[0]
        assert edu.start_date.value == "Oct 2018"
        assert edu.end_date.value == "Sep 2021"

    def test_raw_sections_are_captured(self, parsed):
        assert "experience" in parsed.raw_sections
        assert "education" in parsed.raw_sections
        assert "skills" in parsed.raw_sections
