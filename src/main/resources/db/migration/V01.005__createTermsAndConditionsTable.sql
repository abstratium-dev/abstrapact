-- Terms and Conditions Table
-- Creates the catalogue table for reusable T&C documents

CREATE TABLE T_terms_and_conditions (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    content_fr TEXT,
    content_de TEXT,
    content_en TEXT,
    current_version VARCHAR(50),
    effective_from DATE,
    effective_until DATE
);

CREATE INDEX I_terms_and_conditions_code ON T_terms_and_conditions(code);
CREATE INDEX I_terms_and_conditions_organisation ON T_terms_and_conditions(organisation_id);
