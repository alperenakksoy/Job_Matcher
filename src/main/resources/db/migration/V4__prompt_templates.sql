-- Week 4: LLM completion layer.
--
-- Owned by Spring's Flyway (single source of schema truth), but read/written
-- directly by the Python ml-service over its own psycopg connection to this
-- same Postgres instance - there's one database, and ml-service already
-- talks to it directly for pgvector (Week 5), so this is consistent with
-- that rather than introducing a second schema-management tool.

CREATE TABLE prompt_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,       -- e.g. 'resume_field_completion'
    version       INTEGER NOT NULL,
    provider      VARCHAR(50) NOT NULL,        -- e.g. 'gemini', 'groq' - which provider this prompt was tuned for
    prompt        TEXT NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),

    -- Only one active version per (name, provider) at a time - the app
    -- picks the active row rather than always "the latest", so a bad
    -- version can be rolled back by flipping this flag instead of deleting
    -- history needed for the Week 9 prompt v1 vs v2 vs v3 comparison.
    CONSTRAINT uq_prompt_templates_name_version UNIQUE (name, version)
);

-- Enforces "at most one active row per (name, provider)" - partial unique
-- index instead of a trigger, since Postgres has no direct "unique where
-- active" constraint syntax.
CREATE UNIQUE INDEX uq_prompt_templates_active_per_name_provider
    ON prompt_templates (name, provider)
    WHERE active = true;

CREATE TABLE prompt_executions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_template_id  UUID REFERENCES prompt_templates(id),
    resume_id           UUID REFERENCES app_resumes(id), -- nullable: not every prompt execution is resume-scoped (job parsing later)
    provider            VARCHAR(50) NOT NULL,
    model               VARCHAR(100) NOT NULL, -- e.g. 'gemini-2.0-flash' - provider config can change independent of the prompt version
    input               TEXT NOT NULL,
    output              TEXT,                  -- nullable: a failed call still gets logged, with error_message set instead
    success             BOOLEAN NOT NULL,
    error_message       TEXT,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    duration_ms         INTEGER NOT NULL,
    estimated_cost_usd  NUMERIC(10, 6),
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_prompt_executions_resume_id ON prompt_executions (resume_id);
CREATE INDEX idx_prompt_executions_prompt_template_id ON prompt_executions (prompt_template_id);
CREATE INDEX idx_prompt_executions_created_at ON prompt_executions (created_at);
