-- Support recording rejected webhook events (signature failure, no matching product,
-- malformed payload) in T_webhook_event for audit/debugging purposes.
-- See docs/DESIGN_OF_PAYMENT.md

-- Rejected events may not have a parseable event id or event type.
ALTER TABLE T_webhook_event MODIFY COLUMN psp_event_id VARCHAR(255);
ALTER TABLE T_webhook_event MODIFY COLUMN event_type VARCHAR(100);
ALTER TABLE T_webhook_event MODIFY COLUMN raw_payload TEXT;

-- Record why the webhook was rejected (e.g. "signature verification failed").
ALTER TABLE T_webhook_event ADD COLUMN rejection_reason VARCHAR(500);

-- Update the check constraint to include REJECTED.
ALTER TABLE T_webhook_event DROP CONSTRAINT CHK_webhook_event_processing_result;
ALTER TABLE T_webhook_event
    ADD CONSTRAINT CHK_webhook_event_processing_result
    CHECK (processing_result IN ('PROCESSED', 'DUPLICATE', 'UNMATCHED', 'STALE', 'IGNORED', 'REJECTED'));

-- Mirror on Envers audit table
ALTER TABLE T_webhook_event_AUD ADD COLUMN rejection_reason VARCHAR(500);
