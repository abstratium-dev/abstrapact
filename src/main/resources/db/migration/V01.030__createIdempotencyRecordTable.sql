-- Idempotency record table for deduplicating mutating REST requests
-- See docs/DESIGN_OF_IDEMPOTENCY.md

CREATE TABLE T_idempotency_record (
    id              VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    scope           VARCHAR(50) NOT NULL,        -- 'contract_create', 'contract_accept', 'stripe_api'
    scope_id        VARCHAR(36),                  -- account id or contract id
    request_fingerprint VARCHAR(64) NOT NULL,    -- SHA-256 of the request payload
    status_code     INT NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMP NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    CONSTRAINT UQ_idempotency_key_scope UNIQUE (idempotency_key, scope)
);

CREATE INDEX I_idempotency_record_key ON T_idempotency_record(idempotency_key);
CREATE INDEX I_idempotency_record_scope ON T_idempotency_record(scope, scope_id);
CREATE INDEX I_idempotency_record_expires ON T_idempotency_record(expires_at);
