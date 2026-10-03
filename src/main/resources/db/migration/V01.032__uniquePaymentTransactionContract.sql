-- Idempotency hardening: at most one PENDING payment attempt per contract.
--
-- Two concurrent /accept calls with DIFFERENT idempotency keys can otherwise both
-- pass the OFFERED state check (READ COMMITTED shows the pre-commit state) and each
-- insert a PENDING PaymentTransaction + a separate Stripe Checkout Session, which
-- can double-charge the customer. The contract state machine already guarantees
-- "one contract = one open payment attempt" for serialized requests (a second
-- accept gets 422 once the first commits); this constraint makes the database
-- enforce the same rule under concurrency.
--
-- Implemented as a generated column + unique index (a "partial unique index" for
-- MySQL, which does not support WHERE clauses on indexes): the key is contract_id
-- only while status='PENDING' and NULL otherwise, and unique indexes allow any
-- number of NULLs. This also keeps the door open for a future "retry payment"
-- feature that inserts a new PENDING row after the previous attempt FAILED.
--
-- The losing transaction rolls back on this constraint; the REST layer recovers by
-- looking up the winner's PENDING transaction and returning its checkout URL.
--
-- See docs/DESIGN_OF_IDEMPOTENCY.md §5.4/§5.5.

ALTER TABLE T_payment_transaction
    ADD COLUMN pending_contract_key VARCHAR(36) AS
        (CASE WHEN status = 'PENDING' THEN contract_id ELSE NULL END);

ALTER TABLE T_payment_transaction
    ADD CONSTRAINT UQ_payment_transaction_pending_contract
        UNIQUE (pending_contract_key);
