-- Contract Tables
-- Creates tables for contract instances, line items, terms links, and signatories

CREATE TABLE T_contract (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_reference VARCHAR(50) NOT NULL UNIQUE,
    contract_date DATE,
    currency VARCHAR(3),
    grand_total DECIMAL(19,4) NOT NULL DEFAULT 0.00,
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
    payment_model VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    public_notes TEXT,
    internal_notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT CHK_payment_model CHECK (payment_model IN ('PAY_FIRST', 'BILL_OVER_TIME')),
    CONSTRAINT CHK_contract_state CHECK (state IN ('DRAFT', 'OFFERED', 'ACCEPTED', 'AWAITING_APPROVAL', 'APPROVED', 'RUNNING', 'CANCELLED', 'EXPIRED', 'TERMINATED'))
);

CREATE INDEX I_contract_reference ON T_contract(contract_reference);
CREATE INDEX I_contract_organisation ON T_contract(organisation_id);
CREATE INDEX I_contract_state ON T_contract(state);

CREATE TABLE T_contract_line_item (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36) NOT NULL,
    product_instance_id VARCHAR(36) NOT NULL,
    line_total DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT FK_contract_line_item_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT FK_contract_line_item_product_instance_id
        FOREIGN KEY (product_instance_id) REFERENCES T_product_instance(id)
);

CREATE INDEX I_contract_line_item_contract ON T_contract_line_item(contract_id);
CREATE INDEX I_contract_line_item_product_instance ON T_contract_line_item(product_instance_id);
CREATE INDEX I_contract_line_item_organisation ON T_contract_line_item(organisation_id);

CREATE TABLE T_contract_terms_link (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36) NOT NULL,
    terms_and_conditions_id VARCHAR(36) NOT NULL,
    terms_version_at_signing VARCHAR(50),
    scope VARCHAR(30) NOT NULL,
    contract_line_item_id VARCHAR(36),
    CONSTRAINT FK_contract_terms_link_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT FK_contract_terms_link_terms_id
        FOREIGN KEY (terms_and_conditions_id) REFERENCES T_terms_and_conditions(id),
    CONSTRAINT FK_contract_terms_link_line_item_id
        FOREIGN KEY (contract_line_item_id) REFERENCES T_contract_line_item(id),
    CONSTRAINT CHK_terms_scope CHECK (scope IN ('GENERAL', 'SPECIAL_FOR_LINE_ITEM'))
);

CREATE INDEX I_contract_terms_link_contract ON T_contract_terms_link(contract_id);
CREATE INDEX I_contract_terms_link_terms ON T_contract_terms_link(terms_and_conditions_id);
CREATE INDEX I_contract_terms_link_line_item ON T_contract_terms_link(contract_line_item_id);
CREATE INDEX I_contract_terms_link_organisation ON T_contract_terms_link(organisation_id);

CREATE TABLE T_signatory (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36) NOT NULL,
    signatory_type VARCHAR(20) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role_title VARCHAR(100),
    organisation_name VARCHAR(255),
    signature_date DATE,
    signature_place VARCHAR(100),
    digital_signature_reference VARCHAR(255),
    CONSTRAINT FK_signatory_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT CHK_signatory_type CHECK (signatory_type IN ('SME', 'CUSTOMER'))
);

CREATE INDEX I_signatory_contract ON T_signatory(contract_id);
CREATE INDEX I_signatory_organisation ON T_signatory(organisation_id);
