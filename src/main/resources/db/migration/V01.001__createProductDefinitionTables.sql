-- Product Definition Layer Tables
-- Creates tables for product definitions, parts, attributes, discounts, and services

-- T_product_definition: Blueprint for sellable products
CREATE TABLE T_product_definition (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    billing_model VARCHAR(20) NOT NULL,
    product_valid_from DATE,
    product_valid_until DATE,
    CONSTRAINT CHK_billing_model CHECK (billing_model IN ('FIXED_PRICE', 'SUBSCRIPTION'))
);

CREATE INDEX I_product_definition_product_code ON T_product_definition(product_code);

-- T_service_definition: Services that can be linked to subscription products
CREATE TABLE T_service_definition (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    service_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    service_type VARCHAR(20) NOT NULL,
    target_product_definition_id VARCHAR(36),
    usage_limit INT,
    abstract_service_description VARCHAR(500),
    CONSTRAINT CHK_service_type CHECK (service_type IN ('PRODUCT_ACCESS', 'ABSTRACT_SERVICE')),
    CONSTRAINT FK_service_definition_target_product_definition_id
        FOREIGN KEY (target_product_definition_id) REFERENCES T_product_definition(id)
);

CREATE INDEX I_service_definition_service_code ON T_service_definition(service_code);
CREATE INDEX I_service_definition_target_product ON T_service_definition(target_product_definition_id);

-- T_subscription_entitlement: Validity periods for subscription products
CREATE TABLE T_subscription_entitlement (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_definition_id VARCHAR(36) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    CONSTRAINT FK_subscription_entitlement_product_definition_id
        FOREIGN KEY (product_definition_id) REFERENCES T_product_definition(id)
);

CREATE INDEX I_subscription_entitlement_product ON T_subscription_entitlement(product_definition_id);

-- T_part_definition: Tree structure of product components
CREATE TABLE T_part_definition (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_definition_id VARCHAR(36) NOT NULL,
    parent_part_definition_id VARCHAR(36),
    part_code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    unit_price DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT FK_part_definition_product_definition_id
        FOREIGN KEY (product_definition_id) REFERENCES T_product_definition(id),
    CONSTRAINT FK_part_definition_parent_part_definition_id
        FOREIGN KEY (parent_part_definition_id) REFERENCES T_part_definition(id)
);

CREATE INDEX I_part_definition_product ON T_part_definition(product_definition_id);
CREATE INDEX I_part_definition_parent ON T_part_definition(parent_part_definition_id);

-- T_part_attribute_definition: Defines what attributes a part can have
CREATE TABLE T_part_attribute_definition (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    part_definition_id VARCHAR(36) NOT NULL,
    attribute_name VARCHAR(50) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value VARCHAR(255),
    CONSTRAINT CHK_data_type CHECK (data_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE')),
    CONSTRAINT FK_part_attribute_definition_part_definition_id
        FOREIGN KEY (part_definition_id) REFERENCES T_part_definition(id)
);

CREATE INDEX I_part_attribute_definition_part ON T_part_attribute_definition(part_definition_id);
CREATE UNIQUE INDEX I_part_attribute_definition_name
    ON T_part_attribute_definition(part_definition_id, attribute_name);

-- T_part_attribute_allowed_value: Valid values for constrained attributes
CREATE TABLE T_part_attribute_allowed_value (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    part_attribute_definition_id VARCHAR(36) NOT NULL,
    allowed_value VARCHAR(255) NOT NULL,
    CONSTRAINT FK_part_attribute_allowed_value_attribute_id
        FOREIGN KEY (part_attribute_definition_id) REFERENCES T_part_attribute_definition(id)
);

CREATE INDEX I_part_attr_allowed_value_attribute ON T_part_attribute_allowed_value(part_attribute_definition_id);

-- T_discount_definition: Discounts applied to specific paths in product tree
CREATE TABLE T_discount_definition (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_definition_id VARCHAR(36) NOT NULL,
    path VARCHAR(255) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    priority INT NOT NULL DEFAULT 0,
    active_from DATE,
    active_until DATE,
    CONSTRAINT CHK_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'OVERRIDE')),
    CONSTRAINT FK_discount_definition_product_definition_id
        FOREIGN KEY (product_definition_id) REFERENCES T_product_definition(id)
);

CREATE INDEX I_discount_definition_product ON T_discount_definition(product_definition_id);
CREATE INDEX I_discount_definition_path ON T_discount_definition(path);

-- T_subscription_service_link: Links subscriptions to their services
CREATE TABLE T_subscription_service_link (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    subscription_product_definition_id VARCHAR(36) NOT NULL,
    service_definition_id VARCHAR(36) NOT NULL,
    CONSTRAINT FK_sub_service_link_subscription_product
        FOREIGN KEY (subscription_product_definition_id) REFERENCES T_product_definition(id),
    CONSTRAINT FK_sub_service_link_service
        FOREIGN KEY (service_definition_id) REFERENCES T_service_definition(id)
);

CREATE INDEX I_sub_service_link_subscription ON T_subscription_service_link(subscription_product_definition_id);
CREATE INDEX I_sub_service_link_service ON T_subscription_service_link(service_definition_id);
CREATE UNIQUE INDEX I_sub_service_link_unique
    ON T_subscription_service_link(subscription_product_definition_id, service_definition_id);
