-- Add cross_tenant_api_allowed flag to product definition
-- Also widen product_code to 100 chars to accommodate the {orgId}:: prefix

ALTER TABLE T_product_definition MODIFY COLUMN product_code VARCHAR(100) NOT NULL;
ALTER TABLE T_product_definition ADD COLUMN cross_tenant_api_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE T_product_definition_AUD MODIFY COLUMN product_code VARCHAR(100);
ALTER TABLE T_product_definition_AUD ADD COLUMN cross_tenant_api_allowed BOOLEAN;
