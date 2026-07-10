-- Links a customer account to a contract
CREATE TABLE T_contract_account_role (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    valid_from DATE,
    valid_until DATE,
    CONSTRAINT FK_contract_account_role_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT CHK_contract_account_role_type CHECK (role_type IN ('CUSTOMER'))
);

CREATE INDEX I_contract_account_role_contract ON T_contract_account_role(contract_id);
CREATE INDEX I_contract_account_role_account ON T_contract_account_role(account_id);

CREATE TABLE T_contract_account_role_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    contract_id VARCHAR(36),
    account_id VARCHAR(255),
    role_type VARCHAR(30),
    valid_from DATE,
    valid_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_contract_account_role_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_contract_account_role_aud_rev ON T_contract_account_role_AUD(REV);
CREATE INDEX I_contract_account_role_aud_id ON T_contract_account_role_AUD(id);
