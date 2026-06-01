-- Envers Audit Table for Terms and Conditions

CREATE TABLE T_terms_and_conditions_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    code VARCHAR(50),
    title VARCHAR(255),
    content_fr TEXT,
    content_de TEXT,
    content_en TEXT,
    current_version VARCHAR(50),
    effective_from DATE,
    effective_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_terms_and_conditions_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_terms_and_conditions_aud_rev ON T_terms_and_conditions_AUD(REV);
CREATE INDEX I_terms_and_conditions_aud_id ON T_terms_and_conditions_AUD(id);
