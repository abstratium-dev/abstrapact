-- Envers Auditing Tables
-- Creates REVINFO and audit tables for all product definition and instance entities

-- REVINFO: Custom revision entity table with user attribution and change notes
CREATE TABLE REVINFO (
    REV BIGINT AUTO_INCREMENT PRIMARY KEY,
    REVTSTMP BIGINT,
    username VARCHAR(255),
    correlation_id VARCHAR(255),
    change_note VARCHAR(255)
);

CREATE INDEX I_revinfo_timestamp ON REVINFO(REVTSTMP);
CREATE INDEX I_revinfo_correlation_id ON REVINFO(correlation_id);
CREATE INDEX I_revinfo_change_note ON REVINFO(change_note);
CREATE INDEX I_revinfo_username ON REVINFO(username);

-- T_product_definition_AUD: Audit table for product definitions
CREATE TABLE T_product_definition_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_code VARCHAR(50),
    description VARCHAR(255),
    billing_model VARCHAR(20),
    product_valid_from DATE,
    product_valid_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_product_definition_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_product_definition_aud_rev ON T_product_definition_AUD(REV);
CREATE INDEX I_product_definition_aud_id ON T_product_definition_AUD(id);

-- T_service_definition_AUD: Audit table for service definitions
CREATE TABLE T_service_definition_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    service_code VARCHAR(50),
    description VARCHAR(255),
    service_type VARCHAR(20),
    target_product_definition_id VARCHAR(36),
    usage_limit INT,
    abstract_service_description VARCHAR(500),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_service_definition_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_service_definition_aud_rev ON T_service_definition_AUD(REV);
CREATE INDEX I_service_definition_aud_id ON T_service_definition_AUD(id);

-- T_subscription_entitlement_AUD: Audit table for subscription entitlements
CREATE TABLE T_subscription_entitlement_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_definition_id VARCHAR(36),
    valid_from DATE,
    valid_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_subscription_entitlement_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_subscription_entitlement_aud_rev ON T_subscription_entitlement_AUD(REV);
CREATE INDEX I_subscription_entitlement_aud_id ON T_subscription_entitlement_AUD(id);

-- T_part_definition_AUD: Audit table for part definitions
CREATE TABLE T_part_definition_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_definition_id VARCHAR(36),
    parent_part_definition_id VARCHAR(36),
    part_code VARCHAR(50),
    description VARCHAR(255),
    unit_price DECIMAL(19,4),
    display_order INT,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_definition_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_definition_aud_rev ON T_part_definition_AUD(REV);
CREATE INDEX I_part_definition_aud_id ON T_part_definition_AUD(id);

-- T_part_attribute_definition_AUD: Audit table for part attribute definitions
CREATE TABLE T_part_attribute_definition_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    part_definition_id VARCHAR(36),
    attribute_name VARCHAR(50),
    data_type VARCHAR(20),
    is_required BOOLEAN,
    default_value VARCHAR(255),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_attribute_definition_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_attribute_definition_aud_rev ON T_part_attribute_definition_AUD(REV);
CREATE INDEX I_part_attribute_definition_aud_id ON T_part_attribute_definition_AUD(id);

-- T_part_attribute_allowed_value_AUD: Audit table for allowed values
CREATE TABLE T_part_attribute_allowed_value_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    part_attribute_definition_id VARCHAR(36),
    allowed_value VARCHAR(255),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_attr_allowed_value_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_attr_allowed_value_aud_rev ON T_part_attribute_allowed_value_AUD(REV);
CREATE INDEX I_part_attr_allowed_value_aud_id ON T_part_attribute_allowed_value_AUD(id);

-- T_discount_definition_AUD: Audit table for discount definitions
CREATE TABLE T_discount_definition_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_definition_id VARCHAR(36),
    path VARCHAR(255),
    discount_type VARCHAR(20),
    discount_value DECIMAL(19,4),
    priority INT,
    active_from DATE,
    active_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_discount_definition_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_discount_definition_aud_rev ON T_discount_definition_AUD(REV);
CREATE INDEX I_discount_definition_aud_id ON T_discount_definition_AUD(id);

-- T_subscription_service_link_AUD: Audit table for subscription-service links
CREATE TABLE T_subscription_service_link_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    subscription_product_definition_id VARCHAR(36),
    service_definition_id VARCHAR(36),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_subscription_service_link_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_subscription_service_link_aud_rev ON T_subscription_service_link_AUD(REV);
CREATE INDEX I_subscription_service_link_aud_id ON T_subscription_service_link_AUD(id);

-- T_product_instance_AUD: Audit table for product instances
CREATE TABLE T_product_instance_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_definition_id VARCHAR(36),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_product_instance_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_product_instance_aud_rev ON T_product_instance_AUD(REV);
CREATE INDEX I_product_instance_aud_id ON T_product_instance_AUD(id);

-- T_part_instance_AUD: Audit table for part instances
CREATE TABLE T_part_instance_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    product_instance_id VARCHAR(36),
    part_definition_id VARCHAR(36),
    parent_part_instance_id VARCHAR(36),
    resolved_unit_price DECIMAL(19,4),
    display_order INT,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_instance_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_instance_aud_rev ON T_part_instance_AUD(REV);
CREATE INDEX I_part_instance_aud_id ON T_part_instance_AUD(id);

-- T_part_instance_attribute_AUD: Audit table for part instance attributes
CREATE TABLE T_part_instance_attribute_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    part_instance_id VARCHAR(36),
    attribute_name VARCHAR(50),
    attribute_value VARCHAR(255),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_instance_attribute_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_instance_attribute_aud_rev ON T_part_instance_attribute_AUD(REV);
CREATE INDEX I_part_instance_attribute_aud_id ON T_part_instance_attribute_AUD(id);
