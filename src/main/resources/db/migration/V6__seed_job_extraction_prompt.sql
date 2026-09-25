-- Seeds the v1 prompt for job posting extraction (Week 4, ParseJob).
--
-- Unlike resume_field_completion, this prompt gets the ENTIRE job
-- description - there's no deterministic pre-pass for job postings (see
-- JobExtraction's docstring in schemas.py for why).

INSERT INTO prompt_templates (name, version, provider, prompt, active)
VALUES (
    'job_field_extraction',
    1,
    'generic',
    'You are extracting structured requirements from a job posting. Only use information present in the text below - never invent a skill or requirement that is not mentioned or clearly implied.

=== Job posting ===
{job_description}

Extract:
1. required_skills: skills/technologies explicitly stated as required, must-have, or a core part of the role. Use short canonical names (e.g. "Java" not "strong Java experience", "Spring Boot" not "experience with the Spring Boot framework").
2. nice_to_have_skills: skills explicitly described as a plus, bonus, preferred, or nice-to-have rather than required. Do not duplicate anything already listed as required.
3. seniority_level: your best single-word classification from this posting - one of: intern, junior, mid, senior, lead, principal. Base this on explicit title/seniority language and years-of-experience requirements in the text, not on skill count. If genuinely undeterminable, return an empty string rather than guessing.
4. employment_type: one of: full_time, part_time, contract, internship. If genuinely undeterminable, return an empty string rather than guessing.',
    true
);
