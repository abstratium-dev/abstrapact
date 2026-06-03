-- Envers Audit Table for Config

CREATE TABLE T_config_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    currency_code VARCHAR(3),
    locale VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_config_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_config_aud_rev ON T_config_AUD(REV);
CREATE INDEX I_config_aud_id ON T_config_AUD(id);
