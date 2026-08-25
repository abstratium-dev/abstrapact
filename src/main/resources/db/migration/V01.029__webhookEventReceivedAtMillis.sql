-- Change received_at from TIMESTAMP (second precision) to DATETIME(3) (millisecond precision)
-- so that webhook events arriving in the same second can be ordered correctly.

ALTER TABLE T_webhook_event MODIFY COLUMN received_at DATETIME(3) NOT NULL;
ALTER TABLE T_webhook_event_AUD MODIFY COLUMN received_at DATETIME(3);
