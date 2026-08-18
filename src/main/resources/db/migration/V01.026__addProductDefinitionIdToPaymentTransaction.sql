-- Add product_definition_id to T_payment_transaction so the product definition can be
-- resolved directly from the transaction without joining through contract line items.
-- See docs/DESIGN_OF_PAYMENT.md

ALTER TABLE T_payment_transaction ADD COLUMN product_definition_id VARCHAR(36) NOT NULL;
ALTER TABLE T_payment_transaction
    ADD CONSTRAINT FK_payment_transaction_product_definition
    FOREIGN KEY (product_definition_id) REFERENCES T_product_definition(id);

CREATE INDEX I_payment_transaction_product_def ON T_payment_transaction(product_definition_id);

-- Mirror on Envers audit table
ALTER TABLE T_payment_transaction_AUD ADD COLUMN product_definition_id VARCHAR(36);
