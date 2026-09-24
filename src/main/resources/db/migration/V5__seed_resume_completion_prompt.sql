-- Seeds the v1 prompt for resume field completion (Week 4).
--
-- Seeding reference/config data via Flyway (not just schema) is a
-- deliberate choice here: this row needs to exist before ml-service's
-- first request, and Flyway running before the app starts guarantees
-- that ordering without a separate "seed script" step to remember.

INSERT INTO prompt_templates (name, version, provider, prompt, active)
VALUES (
    'resume_field_completion',
    1,
    'generic',
    'You are extracting specific missing fields from a resume. Only use information present in the text below - never invent a value. If a field truly cannot be determined from the text, leave it as an empty string.

=== Resume header (top of document) ===
{header_snippet}

=== Education section (raw text, {education_count} entries in order from top to bottom) ===
{education_section}

Task 1: Extract the candidate''s full name from the header section.

Task 2: The education section above contains exactly {education_count} entries. For each entry, in the same top-to-bottom order it appears in the text, extract:
- institution: the school/university name
- degree: the degree type (e.g. "M.Sc", "B.Sc", "Bachelor of Science")
- field_of_study: the subject studied (e.g. "Computer Science")

Return one education completion object per entry, with "index" set to its position (0-based, top entry is 0). If the section is empty or has no discernible entries, return an empty list.',
    true
);
