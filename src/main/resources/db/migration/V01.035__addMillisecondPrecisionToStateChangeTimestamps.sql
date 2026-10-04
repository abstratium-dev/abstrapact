-- Increase precision of state change timestamps so milliseconds are preserved.
ALTER TABLE T_process_instance_step MODIFY COLUMN step_timestamp TIMESTAMP(3) NOT NULL;
