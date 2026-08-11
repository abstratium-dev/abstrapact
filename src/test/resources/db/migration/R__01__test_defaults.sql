-- Repeatable migration for test data
-- This runs after all versioned migrations to ensure default data exists.
-- Inserts conditionally!

-- for example: Ensure default organization exists (required for all test data)
--
-- INSERT INTO T_organisations (id, name, created_at)
-- SELECT '00000000-0000-0000-0000-000000000000', 'Default Test Org', CURRENT_TIMESTAMP
-- WHERE NOT EXISTS (SELECT 1 FROM T_organisations WHERE id = '00000000-0000-0000-0000-000000000000');
