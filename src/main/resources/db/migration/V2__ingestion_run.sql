CREATE TABLE ingestion_runs (
                                id UUID PRIMARY KEY,
                                source VARCHAR(50) NOT NULL,
                                started_at TIMESTAMPTZ NOT NULL,
                                completed_at TIMESTAMPTZ,
                                jobs_fetched INT NOT NULL DEFAULT 0,
                                jobs_created INT NOT NULL DEFAULT 0,
                                jobs_skipped INT NOT NULL DEFAULT 0,
                                status VARCHAR(20) NOT NULL,
                                error_message TEXT,
                                created_at TIMESTAMPTZ NOT NULL,
                                updated_at TIMESTAMPTZ NOT NULL
);