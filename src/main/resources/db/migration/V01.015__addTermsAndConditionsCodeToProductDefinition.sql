-- Add terms_and_conditions_code to product definition

ALTER TABLE T_product_definition ADD COLUMN terms_and_conditions_code VARCHAR(50);

CREATE INDEX I_product_definition_terms_code ON T_product_definition(terms_and_conditions_code);

-- Update audit table
ALTER TABLE T_product_definition_AUD ADD COLUMN terms_and_conditions_code VARCHAR(50);
