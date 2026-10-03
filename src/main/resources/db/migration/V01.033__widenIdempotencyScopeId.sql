-- Widen scope_id: for contract_create it stores the JWT principal name
-- (email / sub claim), which can exceed 36 characters.
ALTER TABLE T_idempotency_record MODIFY COLUMN scope_id VARCHAR(255);
