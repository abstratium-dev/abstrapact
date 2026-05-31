---
trigger: glob
globs: src/main/java/**/service/**/*.java
---

**No Bulk Operations with EntityManager.** Never use bulk `DELETE WHERE` or `UPDATE WHERE` operations (`executeUpdate()`, `CriteriaUpdate`, `CriteriaDelete`) because Hibernate Envers cannot audit them. Always load entities and use `em.remove()` or field updates within a transaction.

**@TenantId Constraint.** When using Hibernate multi-tenancy with `@TenantId`, bulk operations bypass the tenant discriminator filter and can modify data across all tenants. Always use entity-level operations to ensure proper tenant isolation.

**EntityManager.find() Constraint.** `entityManager.find(...)` bypasses tenant filtering and can return entities from other tenants. Use/Add a `findById(String id)` method to a class in the `service` pacakge instead, which applies tenant filtering automatically, e.g.:

```
public Optional<OAuthClient> findById(String id) {
    var query = em.createQuery("SELECT c FROM OAuthClient c WHERE c.id = :id", OAuthClient.class);
    query.setParameter("id", id);
    return query.getResultList().stream().findFirst();
}
```
