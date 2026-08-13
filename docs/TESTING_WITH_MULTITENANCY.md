## Testing with Discriminator-Based Multitenancy

Entities use `@TenantId` on their `orgId` field. `JwtOrgResolver` implements `TenantResolver`, extracting `orgId` from the JWT Bearer token at runtime, and falling back to the default org UUID when none is present. The default UUID is injected from the `default.org.uuid` configuration property and set via the `DEFAULT_ORG_UUID` environment variable before tests run. In dev/test mode it defaults to `00000000-0000-0000-0000-000000000000`.

### Required Configuration

```properties
quarkus.hibernate-orm.multitenant=DISCRIMINATOR
```

### Required TenantResolver Pattern

`@PersistenceUnitExtension` + `@RequestScoped` with a try-catch fallback in `resolveTenantId()` is mandatory. Without the catch, `@QuarkusTest` service tests fail with:

```
HibernateException: SessionFactory configured for multi-tenancy, but no tenant identifier specified
```

This happens because no HTTP request context exists in service tests, so `request.getHeader()` throws. The catch block must return the default:

```java
@PersistenceUnitExtension
@RequestScoped
public class JwtOrgResolver implements TenantResolver {

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @Inject HttpServerRequest request;

    @Override
    public String getDefaultTenantId() { return defaultOrgId; }

    @Override
    public String resolveTenantId() {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return defaultOrgId;
            String orgId = extractOrgIdFromJwt(authHeader.substring(7));
            return orgId != null ? orgId : defaultOrgId;
        } catch (Exception e) {
            return defaultOrgId;  // no HTTP context in service tests
        }
    }
}
```

| Mistake | Result |
|---------|--------|
| No try-catch in `resolveTenantId()` | `HibernateException` in tests |
| Not implementing `getDefaultTenantId()` | Runtime failure |
| Missing `@PersistenceUnitExtension` | Resolver not discovered |
| Missing `quarkus.hibernate-orm.multitenant=DISCRIMINATOR` | `HibernateException` in tests |
| Returning `null` from `resolveTenantId()` | `HibernateException` |

### Service Tests

`@QuarkusTest` service tests work without any mocking — operations automatically land in the default org defined by `default.org.uuid`:

```java
@QuarkusTest
public class MyServiceTest {
    @Inject MyService service;

    @Test
    @Transactional
    public void testCreate() {
        // entity is persisted with the default org UUID automatically
        MyEntity created = service.create(...);
        assertNotNull(created.getId());
    }
}
```

### Cross-Org Isolation Tests

To insert data into a specific org (bypassing Hibernate's tenant filter), use native SQL:

```java
@Inject EntityManager em;

em.createNativeQuery(
    "INSERT INTO T_my_table (id, org_id, ...) VALUES (:id, :orgId, ...)")
    .setParameter("id", UUID.randomUUID().toString())
    .setParameter("orgId", "specific-org-uuid")
    .executeUpdate();
```

For REST API tests, set the tenant via a JWT with the `orgId` claim:

```java
private String generateToken(String accountId, String orgId, Set<String> groups) {
    return Jwt.issuer("https://abstrauth.abstratium.dev")
        .subject(accountId)
        .groups(groups)
        .claim("orgId", orgId)
        .sign();
}
```

