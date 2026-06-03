-- Config Table
-- Stores per-organisation UI configuration (currency, locale, etc.)

CREATE TABLE T_config (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    locale VARCHAR(20) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX I_config_organisation ON T_config(organisation_id);
