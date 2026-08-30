"""Extracts raw text from a PDF's bytes using PyMuPDF (fitz).

Kept deliberately separate from the regex/NLP extraction logic in
deterministic_parser.py - this module's only job is "PDF bytes in,
plain text out" so it stays easy to swap out (e.g. for OCR fallback
on scanned resumes) without touching the extraction rules.
"""

import pymupdf


class PdfExtractionError(Exception):
    """Raised when the PDF can't be opened or contains no extractable text."""


def extract_text(file_content: bytes) -> str:
    if not file_content.startswith(b"%PDF-"):
        raise PdfExtractionError("File does not appear to be a valid PDF (missing %PDF- header)")

    try:
        with pymupdf.open(stream=file_content, filetype="pdf") as doc:
            pages_text = [page.get_text() for page in doc]
    except Exception as e:  # PyMuPDF raises its own RuntimeError/ValueError variants
        raise PdfExtractionError(f"Failed to open/read PDF: {e}") from e

    text = "\n".join(pages_text).strip()

    if not text:
        # Most likely a scanned/image-only PDF - out of scope for the
        # deterministic parser this week (OCR would be a separate step).
        raise PdfExtractionError(
            "PDF contains no extractable text (likely a scanned image without OCR)"
        )

    return text
