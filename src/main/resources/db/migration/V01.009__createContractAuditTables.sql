-- Envers Audit Tables for Contract Entities

CREATE TABLE T_contract_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_reference VARCHAR(50),
    contract_date DATE,
    currency VARCHAR(3),
    grand_total DECIMAL(19,4),
    billing_address_line1 VARCHAR(255),
    billing_address_line2 VARCHAR(255),
    billing_city VARCHAR(100),
    billing_postcode VARCHAR(20),
    billing_country VARCHAR(2),
    delivery_address_line1 VARCHAR(255),
    delivery_address_line2 VARCHAR(255),
    delivery_city VARCHAR(100),
    delivery_postcode VARCHAR(20),
    delivery_country VARCHAR(2),
    payment_terms VARCHAR(50),
    payment_model VARCHAR(20),
    state VARCHAR(20),
    public_notes TEXT,
    internal_notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_contract_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_contract_aud_rev ON T_contract_AUD(REV);
CREATE INDEX I_contract_aud_id ON T_contract_AUD(id);

CREATE TABLE T_contract_line_item_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    product_instance_id VARCHAR(36),
    line_total DECIMAL(19,4),
    display_order INT,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_contract_line_item_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_contract_line_item_aud_rev ON T_contract_line_item_AUD(REV);
CREATE INDEX I_contract_line_item_aud_id ON T_contract_line_item_AUD(id);

CREATE TABLE T_contract_terms_link_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    terms_and_conditions_id VARCHAR(36),
    terms_version_at_signing VARCHAR(50),
    scope VARCHAR(30),
    contract_line_item_id VARCHAR(36),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_contract_terms_link_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_contract_terms_link_aud_rev ON T_contract_terms_link_AUD(REV);
CREATE INDEX I_contract_terms_link_aud_id ON T_contract_terms_link_AUD(id);

CREATE TABLE T_signatory_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    signatory_type VARCHAR(20),
    full_name VARCHAR(255),
    role_title VARCHAR(100),
    organisation_name VARCHAR(255),
    signature_date DATE,
    signature_place VARCHAR(100),
    digital_signature_reference VARCHAR(255),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_signatory_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_signatory_aud_rev ON T_signatory_AUD(REV);
CREATE INDEX I_signatory_aud_id ON T_signatory_AUD(id);
