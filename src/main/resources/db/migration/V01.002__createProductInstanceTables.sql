-- Product Instance Layer Tables
-- Creates tables for product instances and part instances (concrete configurations)

-- T_product_instance: Concrete configurations for specific customers/sales
CREATE TABLE T_product_instance (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_definition_id VARCHAR(36) NOT NULL,
    CONSTRAINT FK_product_instance_product_definition_id
        FOREIGN KEY (product_definition_id) REFERENCES T_product_definition(id)
);

CREATE INDEX I_product_instance_product ON T_product_instance(product_definition_id);

-- T_part_instance: Tree structure of included parts within a product instance
CREATE TABLE T_part_instance (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    product_instance_id VARCHAR(36) NOT NULL,
    part_definition_id VARCHAR(36) NOT NULL,
    parent_part_instance_id VARCHAR(36),
    resolved_unit_price DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT FK_part_instance_product_instance_id
        FOREIGN KEY (product_instance_id) REFERENCES T_product_instance(id),
    CONSTRAINT FK_part_instance_part_definition_id
        FOREIGN KEY (part_definition_id) REFERENCES T_part_definition(id),
    CONSTRAINT FK_part_instance_parent_part_instance_id
        FOREIGN KEY (parent_part_instance_id) REFERENCES T_part_instance(id)
);

CREATE INDEX I_part_instance_product ON T_part_instance(product_instance_id);
CREATE INDEX I_part_instance_definition ON T_part_instance(part_definition_id);
CREATE INDEX I_part_instance_parent ON T_part_instance(parent_part_instance_id);

-- T_part_instance_attribute: Actual attribute values for part instances
CREATE TABLE T_part_instance_attribute (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    part_instance_id VARCHAR(36) NOT NULL,
    attribute_name VARCHAR(50) NOT NULL,
    attribute_value VARCHAR(255) NOT NULL,
    CONSTRAINT FK_part_instance_attribute_part_instance_id
        FOREIGN KEY (part_instance_id) REFERENCES T_part_instance(id)
);

CREATE INDEX I_part_instance_attr_instance ON T_part_instance_attribute(part_instance_id);
CREATE UNIQUE INDEX I_part_instance_attr_name
    ON T_part_instance_attribute(part_instance_id, attribute_name);
