-- Prefix all existing product codes with their organisation_id
-- Stored format: {orgId}::{rawProductCode}
-- This is idempotent: only rows where the code does not already contain '::' are updated.

UPDATE T_product_definition
SET product_code = CONCAT(organisation_id, '::', product_code)
WHERE product_code NOT LIKE '%::%';
