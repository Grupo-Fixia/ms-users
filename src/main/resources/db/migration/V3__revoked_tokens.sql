CREATE TABLE revoked_tokens (
    jti         VARCHAR(64)              PRIMARY KEY,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
