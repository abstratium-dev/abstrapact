-- Add checkout_url to payment transaction for idempotency replay
ALTER TABLE T_payment_transaction ADD COLUMN checkout_url VARCHAR(500);
ALTER TABLE T_payment_transaction_AUD ADD COLUMN checkout_url VARCHAR(500);
