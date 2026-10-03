-- Payment idempotency hardening: add EXPIRED payment status.
--
-- A Stripe Checkout Session that expires without being paid must be recorded so
-- that the customer can start a NEW payment attempt via POST .../retry-payment.
-- The generated column UQ_payment_transaction_pending_contract already allows a
-- new PENDING row once the old one leaves PENDING.
--
-- See docs/DESIGN_OF_IDEMPOTENCY.md.

ALTER TABLE T_payment_transaction
    DROP CONSTRAINT CHK_payment_transaction_status;

ALTER TABLE T_payment_transaction
    ADD CONSTRAINT CHK_payment_transaction_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'STALE', 'EXPIRED'));
