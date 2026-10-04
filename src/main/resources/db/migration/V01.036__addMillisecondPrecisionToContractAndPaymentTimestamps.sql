-- Increase precision of contract and payment transaction timestamps so milliseconds are preserved.
ALTER TABLE T_contract MODIFY COLUMN created_at TIMESTAMP(3);
ALTER TABLE T_contract MODIFY COLUMN updated_at TIMESTAMP(3);

ALTER TABLE T_payment_transaction MODIFY COLUMN created_at TIMESTAMP(3) NOT NULL;
ALTER TABLE T_payment_transaction MODIFY COLUMN updated_at TIMESTAMP(3) NOT NULL;
