CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
                       id            UUID PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       enabled       BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ NOT NULL,
                       updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role    VARCHAR(50) NOT NULL,
                            CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
                                REFERENCES users(id) ON DELETE CASCADE
);