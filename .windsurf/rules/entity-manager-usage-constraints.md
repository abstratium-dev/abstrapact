---
trigger: glob
globs: src/main/java/**/service/**/*.java
---

**No Bulk Operations with EntityManager.** Never use bulk `DELETE WHERE` or `UPDATE WHERE` operations (`executeUpdate()`, `CriteriaUpdate`, `CriteriaDelete`) because Hibernate Envers cannot audit them. Always load entities and use `em.remove()` or field updates within a transaction.

**@TenantId Constraint.** When using Hibernate multi-tenancy with `@TenantId`, bulk operations bypass the tenant discriminator filter and can modify data across all tenants. Always use entity-level operations to ensure proper tenant isolation.