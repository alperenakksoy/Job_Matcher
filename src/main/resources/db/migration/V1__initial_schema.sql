CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_users (
                           id            UUID PRIMARY KEY,
                           email         VARCHAR(255) NOT NULL UNIQUE,
                           password_hash VARCHAR(255) NOT NULL,
                           role          VARCHAR(20) NOT NULL,
                           enabled       BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at    TIMESTAMPTZ NOT NULL,
                           updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_resumes (
                             id UUID PRIMARY KEY,
                             user_id UUID NOT NULL,
                             original_filename VARCHAR(255) NOT NULL,
                             storage_path VARCHAR(512) NOT NULL,
                             file_hash VARCHAR(64) NOT NULL,
                             file_size_bytes BIGINT NOT NULL,
                             version INT NOT NULL DEFAULT 1,
                             status VARCHAR(50) NOT NULL,
                             parsed_data JSONB,
                             parse_error VARCHAR(1000),
                             created_at TIMESTAMPTZ NOT NULL,
                             updated_at TIMESTAMPTZ NOT NULL,
                             CONSTRAINT fk_resumes_users FOREIGN KEY (user_id)
                                 REFERENCES app_users(id) ON DELETE CASCADE,
                             CONSTRAINT uk_resumes_user_file_hash UNIQUE (user_id, file_hash)
);

CREATE TABLE app_jobs (
                          id UUID PRIMARY KEY,
                          external_id VARCHAR(255) NOT NULL,
                          title VARCHAR(255) NOT NULL,
                          company VARCHAR(255) NOT NULL,
                          location VARCHAR(255),
                          description TEXT NOT NULL,
                          source VARCHAR(30) NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          remote_type VARCHAR(20),
                          apply_url VARCHAR(1000),
                          posted_at TIMESTAMPTZ,
                          ingested_at TIMESTAMPTZ NOT NULL,
                          raw_payload JSONB NOT NULL,
                          parsed_requirements JSONB,
                          parse_error VARCHAR(1000),
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL,
                          CONSTRAINT uq_jobs_external_source UNIQUE (external_id, source)
);

CREATE TABLE refresh_tokens (
                                id         UUID PRIMARY KEY,
                                user_id    UUID NOT NULL,
                                token      VARCHAR(255) NOT NULL UNIQUE,
                                expiry     TIMESTAMPTZ NOT NULL,
                                revoked    BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMPTZ NOT NULL,
                                updated_at TIMESTAMPTZ NOT NULL,
                                CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
                                    REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_jobs_posted_at ON app_jobs(posted_at);
CREATE INDEX idx_jobs_status ON app_jobs(status);

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