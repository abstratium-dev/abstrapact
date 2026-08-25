-- Widen stripe_secret_key and stripe_webhook_secret columns to accommodate
-- modern Stripe key formats which can exceed 100 characters.
-- See docs/DESIGN_OF_PAYMENT.md

ALTER TABLE T_product_definition MODIFY COLUMN stripe_secret_key VARCHAR(255);
ALTER TABLE T_product_definition MODIFY COLUMN stripe_webhook_secret VARCHAR(255);

ALTER TABLE T_product_definition_AUD MODIFY COLUMN stripe_secret_key VARCHAR(255);
ALTER TABLE T_product_definition_AUD MODIFY COLUMN stripe_webhook_secret VARCHAR(255);
