-- Choice group support for T_part_definition
-- A choice group belongs to a parent part and declares how many child parts
-- from that group must appear in a valid product instance.

CREATE TABLE T_part_definition_choice_group (
    id VARCHAR(36) PRIMARY KEY,
    organisation_id VARCHAR(36) NOT NULL,
    parent_part_definition_id VARCHAR(36) NOT NULL,
    min_choices INT NOT NULL DEFAULT 1,
    max_choices INT NOT NULL DEFAULT 1,
    CONSTRAINT FK_part_definition_choice_group_parent
        FOREIGN KEY (parent_part_definition_id) REFERENCES T_part_definition(id)
);

CREATE INDEX I_part_definition_choice_group_parent ON T_part_definition_choice_group(parent_part_definition_id);
CREATE INDEX I_part_definition_choice_group_org ON T_part_definition_choice_group(organisation_id);

CREATE TABLE T_part_definition_choice_group_AUD (
    id VARCHAR(36) NOT NULL,
    organisation_id VARCHAR(36),
    parent_part_definition_id VARCHAR(36),
    min_choices INT,
    max_choices INT,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_part_definition_choice_group_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_part_definition_choice_group_aud_rev ON T_part_definition_choice_group_AUD(REV);
CREATE INDEX I_part_definition_choice_group_aud_id ON T_part_definition_choice_group_AUD(id);

ALTER TABLE T_part_definition
    ADD COLUMN choice_group_id VARCHAR(36);

ALTER TABLE T_part_definition
    ADD CONSTRAINT FK_part_definition_choice_group_id
        FOREIGN KEY (choice_group_id) REFERENCES T_part_definition_choice_group(id);

ALTER TABLE T_part_definition_AUD
    ADD COLUMN choice_group_id VARCHAR(36);
