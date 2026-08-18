-- Payment handling: per-product Stripe credentials + payment transaction + webhook event audit log
-- See docs/DESIGN_OF_PAYMENT.md

-- 1. Per-product Stripe credentials and B2C redirect URLs on T_product_definition
ALTER TABLE T_product_definition ADD COLUMN stripe_secret_key VARCHAR(100);
ALTER TABLE T_product_definition ADD COLUMN stripe_webhook_secret VARCHAR(100);
ALTER TABLE T_product_definition ADD COLUMN payment_success_redirect_url VARCHAR(500);
ALTER TABLE T_product_definition ADD COLUMN payment_cancel_redirect_url VARCHAR(500);

-- Mirror the new columns on the Envers audit table so @Audited history captures them.
ALTER TABLE T_product_definition_AUD ADD COLUMN stripe_secret_key VARCHAR(100);
ALTER TABLE T_product_definition_AUD ADD COLUMN stripe_webhook_secret VARCHAR(100);
ALTER TABLE T_product_definition_AUD ADD COLUMN payment_success_redirect_url VARCHAR(500);
ALTER TABLE T_product_definition_AUD ADD COLUMN payment_cancel_redirect_url VARCHAR(500);

-- 2. Payment transaction: one row per payment attempt per contract
CREATE TABLE T_payment_transaction (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36) NOT NULL,
    psp_identifier VARCHAR(30) NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    psp_session_id VARCHAR(255),
    psp_transaction_ref VARCHAR(255),
    gross_amount DECIMAL(19, 4) NOT NULL,
    fee_amount DECIMAL(19, 4),
    net_amount DECIMAL(19, 4),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_payment_transaction_contract
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT UQ_payment_transaction_correlation
        UNIQUE (correlation_id),
    CONSTRAINT CHK_payment_transaction_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'STALE'))
);

CREATE INDEX I_payment_transaction_contract ON T_payment_transaction(contract_id);
CREATE INDEX I_payment_transaction_correlation ON T_payment_transaction(correlation_id);
CREATE INDEX I_payment_transaction_org ON T_payment_transaction(organisation_id);
CREATE INDEX I_payment_transaction_session ON T_payment_transaction(psp_session_id);
CREATE INDEX I_payment_transaction_status ON T_payment_transaction(status);

-- 3. Webhook event: audit log of every verified webhook call (matched, unmatched, stale, duplicate)
CREATE TABLE T_webhook_event (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36),
    psp_identifier VARCHAR(30) NOT NULL,
    psp_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(36),
    payment_transaction_id VARCHAR(36),
    matched BOOLEAN NOT NULL,
    processing_result VARCHAR(30) NOT NULL,
    raw_payload TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL,
    CONSTRAINT UQ_webhook_event_psp_event
        UNIQUE (psp_identifier, psp_event_id),
    CONSTRAINT FK_webhook_event_payment_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES T_payment_transaction(id),
    CONSTRAINT CHK_webhook_event_processing_result
        CHECK (processing_result IN ('PROCESSED', 'DUPLICATE', 'UNMATCHED', 'STALE', 'IGNORED'))
);

CREATE INDEX I_webhook_event_correlation ON T_webhook_event(correlation_id);
CREATE INDEX I_webhook_event_received ON T_webhook_event(received_at);
CREATE INDEX I_webhook_event_matched ON T_webhook_event(matched);
CREATE INDEX I_webhook_event_processing_result ON T_webhook_event(processing_result);
CREATE INDEX I_webhook_event_org ON T_webhook_event(organisation_id);

-- 4. Envers audit tables for the two new entities
CREATE TABLE T_payment_transaction_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    psp_identifier VARCHAR(30),
    correlation_id VARCHAR(36),
    psp_session_id VARCHAR(255),
    psp_transaction_ref VARCHAR(255),
    gross_amount DECIMAL(19, 4),
    fee_amount DECIMAL(19, 4),
    net_amount DECIMAL(19, 4),
    currency VARCHAR(3),
    status VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_payment_transaction_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_payment_transaction_aud_rev ON T_payment_transaction_AUD(REV);
CREATE INDEX I_payment_transaction_aud_id ON T_payment_transaction_AUD(id);

CREATE TABLE T_webhook_event_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    psp_identifier VARCHAR(30),
    psp_event_id VARCHAR(255),
    event_type VARCHAR(100),
    correlation_id VARCHAR(36),
    payment_transaction_id VARCHAR(36),
    matched BOOLEAN,
    processing_result VARCHAR(30),
    raw_payload TEXT,
    received_at TIMESTAMP,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_webhook_event_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_webhook_event_aud_rev ON T_webhook_event_AUD(REV);
CREATE INDEX I_webhook_event_aud_id ON T_webhook_event_AUD(id);
