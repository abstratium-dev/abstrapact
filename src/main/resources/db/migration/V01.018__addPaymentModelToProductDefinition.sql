-- Add payment model to product definition

ALTER TABLE T_product_definition ADD COLUMN payment_model VARCHAR(20) NOT NULL DEFAULT 'PREPAID';

ALTER TABLE T_product_definition_AUD ADD COLUMN payment_model VARCHAR(20);
