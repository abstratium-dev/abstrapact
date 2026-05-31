-- Add min/max cardinality columns to T_part_definition
-- Default values ensure existing parts have cardinality 1..1 (single instance)
-- Using separate statements for H2/MySQL compatibility

ALTER TABLE T_part_definition ADD COLUMN min_cardinality INT NOT NULL DEFAULT 1;
ALTER TABLE T_part_definition ADD COLUMN max_cardinality INT NOT NULL DEFAULT 1;

-- Also add to Envers audit table
ALTER TABLE T_part_definition_AUD ADD COLUMN min_cardinality INT;
ALTER TABLE T_part_definition_AUD ADD COLUMN max_cardinality INT;
