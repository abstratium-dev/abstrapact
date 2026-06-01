-- Process Instance Tables
-- Creates generic process orchestration tables

CREATE TABLE T_process_instance (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36),
    process_name VARCHAR(100) NOT NULL,
    process_version VARCHAR(20),
    state VARCHAR(20) NOT NULL,
    CONSTRAINT FK_process_instance_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT CHK_process_instance_state CHECK (state IN ('TO_BE_STARTED', 'IN_PROGRESS', 'BLOCKED', 'FAILED', 'COMPLETED'))
);

CREATE INDEX I_process_instance_contract ON T_process_instance(contract_id);
CREATE INDEX I_process_instance_organisation ON T_process_instance(organisation_id);
CREATE INDEX I_process_instance_state ON T_process_instance(state);

CREATE TABLE T_process_instance_step (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    process_instance_id VARCHAR(36) NOT NULL,
    actor_user_id VARCHAR(36),
    step_timestamp TIMESTAMP NOT NULL,
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT FK_process_instance_step_process_instance_id
        FOREIGN KEY (process_instance_id) REFERENCES T_process_instance(id)
);

CREATE INDEX I_process_instance_step_process ON T_process_instance_step(process_instance_id);
CREATE INDEX I_process_instance_step_timestamp ON T_process_instance_step(step_timestamp);
CREATE INDEX I_process_instance_step_organisation ON T_process_instance_step(organisation_id);
