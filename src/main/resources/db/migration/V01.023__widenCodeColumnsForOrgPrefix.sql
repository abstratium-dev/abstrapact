-- Widen part_code to accommodate org-id prefix ({orgId}::{rawCode})
ALTER TABLE T_part_definition
    MODIFY COLUMN part_code VARCHAR(100) NOT NULL;

ALTER TABLE T_part_definition_AUD
    MODIFY COLUMN part_code VARCHAR(100);

-- Widen terms_and_conditions_code on product_definition for org-id prefix
ALTER TABLE T_product_definition
    MODIFY COLUMN terms_and_conditions_code VARCHAR(100);

ALTER TABLE T_product_definition_AUD
    MODIFY COLUMN terms_and_conditions_code VARCHAR(100);

-- Widen code on T_terms_and_conditions for org-id prefix
ALTER TABLE T_terms_and_conditions
    MODIFY COLUMN code VARCHAR(100) NOT NULL;

ALTER TABLE T_terms_and_conditions_AUD
    MODIFY COLUMN code VARCHAR(100);
