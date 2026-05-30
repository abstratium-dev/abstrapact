## Testing with Multitenancy: Testing Services with @TenantId Entities

### Overview

The application uses discriminator-based multitenancy via Hibernate's `@TenantId` annotation on entities like `OAuthClient`, `AccountRole`, `ClientSecret`, etc. The `JwtOrgResolver` extracts the `orgId` from the JWT Bearer token at runtime and Hibernate automatically filters all queries by this tenant ID.

### Key Components

- **Tenant Resolver**: `JwtOrgResolver` implements `TenantResolver`
- **Default Org ID**: `00000000-0000-0000-0000-000000000000` (used when no JWT present)
- **Entity Marking**: Entities use `@TenantId` on the `orgId` field (e.g., `OAuthClient`)

---

### Testing Service Classes with @TenantId Entities

The simplest approach for testing services that use `@TenantId` entities is to use `@QuarkusTest` and let the resolver automatically fall back to the default org:

```java
@QuarkusTest
public class OAuthClientServiceTest {

    @Inject
    OAuthClientService oauthClientService;

    @Test
    @Transactional
    public void testCreate() {
        // Creates client in DEFAULT_ORG_ID automatically
        OAuthClient client = new OAuthClient();
        client.setClientId("test-client");
        client.setClientName("Test Client");
        // ... set other fields

        OAuthClient created = oauthClientService.create(client);

        assertNotNull(created);
        assertNotNull(created.getId());
        // Verify it can be found
        Optional<OAuthClient> found = oauthClientService.findByClientId(created.getClientId());
        assertTrue(found.isPresent());
    }
}
```

**How it works**: When no HTTP request context exists, `JwtOrgResolver.resolveTenantId()` automatically returns `DEFAULT_ORG_ID`. All JPA operations automatically use this tenant, so you don't need to mock anything.

---

### Testing Cross-Org Isolation

When you need to test that data from different orgs is properly isolated, you have two options:

#### Option A: Native SQL for Direct DB Insertion

Use native SQL to insert data with explicit `org_id` values, bypassing Hibernate's tenant filter:

```java
@Inject
EntityManager em;

@Inject
UserTransaction userTransaction;

// Create clients in different orgs
String clientIdA = "client-a";
String orgAId = "org-a-uuid";
em.createNativeQuery(
    "INSERT INTO T_oauth_clients (id, client_id, client_name, " +
    "client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
    "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
    .setParameter("id", UUID.randomUUID().toString())
    .setParameter("clientId", clientIdA)
    .setParameter("name", "Client A")
    .setParameter("orgId", orgAId)  // Explicit org
    .executeUpdate();
```

**Pattern from**: `MultiTenancySecurityTest.java`, `CrossOrgIsolationTest.java`

#### Option B: JWT Token with Specific orgId

For REST API tests, generate a JWT with the desired `orgId` claim:

```java
import io.smallrye.jwt.build.Jwt;

private String generateToken(String accountId, String orgId, Set<String> groups) {
    return Jwt.issuer("https://abstrauth.abstratium.dev")
        .subject(accountId)
        .upn("test_" + accountId + "@example.com")
        .groups(groups)
        .claim("orgId", orgId)  // Sets the tenant
        .sign();
}

// Use with REST Assured
given()
    .auth().oauth2(generateToken(accountId, orgId, Set.of("abstratium-abstrauth_user")))
    .when()
    .get("/api/clients")
    .then()
    .statusCode(200);
```

---

### Complete Example: Service Test with @TenantId Entities

From `OAuthClientServiceTest.java`:

```java
@QuarkusTest
public class OAuthClientServiceTest {

    @Inject
    OAuthClientService oauthClientService;
    
    @Inject
    ClientSecretService clientSecretService;

    @Test
    @Transactional
    public void testCreate() {
        OAuthClient client = new OAuthClient();
        client.setClientId("test-client-" + System.currentTimeMillis());
        client.setClientName("Test Client");
        client.setClientType("public");
        client.setRedirectUris("[\"http://localhost:3000/callback\"]");
        client.setAllowedScopes("[\"openid\", \"profile\"]");
        client.setRequirePkce(true);

        // Creates in DEFAULT_ORG_ID - no mocking needed!
        OAuthClient created = oauthClientService.create(client);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());

        // Read back verifies tenant filtering works
        Optional<OAuthClient> found = oauthClientService.findByClientId(created.getClientId());
        assertTrue(found.isPresent());
    }

    @Test
    @Transactional
    public void testClientSecretMatches() {
        // Create client
        OAuthClient client = new OAuthClient();
        client.setClientId("test-secret-match-" + System.currentTimeMillis());
        // ... set fields
        String secret = "test-secret-123";
        OAuthClient created = oauthClientService.create(client);
        
        // Create related entity (also @TenantId)
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setClientId(created.getClientId());
        clientSecret.setSecretHash(oauthClientService.hashClientSecret(secret));
        clientSecretService.persist(clientSecret);
        
        // Both entities are in DEFAULT_ORG_ID
        assertTrue(oauthClientService.clientSecretMatches(created.getClientId(), secret));
    }
}
```

---

### Setting Up Test Dependencies

When tests need to create related entities (e.g., `OAuthClient` before `AccountRole`), use `UserTransaction` and `EntityManager` directly in `@BeforeEach`:

```java
@QuarkusTest
public class AccountRoleServiceTest {

    @Inject
    AccountRoleService accountRoleService;

    @Inject
    EntityManager em;
    
    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    public void setup() throws Exception {
        userTransaction.begin();
        
        // Ensure test client exists (uses JPA, goes to DEFAULT_ORG_ID)
        var clientQuery = em.createQuery(
            "SELECT c FROM OAuthClient c WHERE c.clientId = :clientId", 
            OAuthClient.class);
        clientQuery.setParameter("clientId", "test_client_123");
        if (clientQuery.getResultList().isEmpty()) {
            OAuthClient client = new OAuthClient();
            client.setClientId("test_client_123");
            // ... set fields
            em.persist(client);  // Automatically gets DEFAULT_ORG_ID
        }
        
        userTransaction.commit();
    }
}
```

---

### Summary Table

| Scenario | Approach |
|----------|----------|
| Testing services with @TenantId entities | `@QuarkusTest` + no special setup (auto-fallback to DEFAULT_ORG_ID) |
| Testing cross-org isolation (create data in specific orgs) | Native SQL with explicit `org_id` |
| Testing REST endpoints with specific org | Generate JWT with `orgId` claim |
| Testing resolver logic itself | Unit test with subclass (see `JwtOrgResolverTest`) |

### Relevant Files

- `@/shared2/abstratium/github.com/abstrauth/src/main/java/dev/abstratium/abstrauth/service/JwtOrgResolver.java` - Tenant resolver
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/service/OAuthClientServiceTest.java` - Service test pattern
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/service/AccountRoleServiceTest.java` - Service with related entities
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/boundary/MultiTenancySecurityTest.java` - Cross-org isolation with native SQL
- `@/shared2/abstratium/github.com/abstrauth/src/test/java/dev/abstratium/abstrauth/service/JwtOrgResolverTest.java` - Unit testing resolver logic
