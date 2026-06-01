-- Envers Audit Tables for Process Instance Entities

CREATE TABLE T_process_instance_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    process_name VARCHAR(100),
    process_version VARCHAR(20),
    state VARCHAR(20),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_process_instance_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_process_instance_aud_rev ON T_process_instance_AUD(REV);
CREATE INDEX I_process_instance_aud_id ON T_process_instance_AUD(id);

CREATE TABLE T_process_instance_step_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    process_instance_id VARCHAR(36),
    actor_user_id VARCHAR(36),
    step_timestamp TIMESTAMP,
    from_state VARCHAR(20),
    to_state VARCHAR(20),
    reason VARCHAR(500),
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_process_instance_step_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_process_instance_step_aud_rev ON T_process_instance_step_AUD(REV);
CREATE INDEX I_process_instance_step_aud_id ON T_process_instance_step_AUD(id);
