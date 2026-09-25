-- prompt_executions was created with only resume_id (Week 4's first pass
-- was resume-only). Job extraction (job_extraction.py) needs the same
-- attribution - added as a new migration rather than editing V4, since
-- V4 may already be applied in some environments.

ALTER TABLE prompt_executions
    ADD COLUMN job_id UUID REFERENCES app_jobs(id);

CREATE INDEX idx_prompt_executions_job_id ON prompt_executions (job_id);
