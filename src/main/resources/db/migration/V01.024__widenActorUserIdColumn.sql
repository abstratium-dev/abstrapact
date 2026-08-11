-- Widen actor_user_id to accommodate email addresses (principal names)
-- Previously VARCHAR(36) was only large enough for UUIDs

ALTER TABLE T_process_instance_step
    MODIFY COLUMN actor_user_id VARCHAR(254);

ALTER TABLE T_process_instance_step_AUD
    MODIFY COLUMN actor_user_id VARCHAR(254);
