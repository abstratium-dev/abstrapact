package dev.abstratium.abstrapact.conditions.boundary;

import dev.abstratium.abstrapact.conditions.boundary.dto.TermsAndConditionsCodeSummary;
import dev.abstratium.abstrapact.conditions.entity.TermsAndConditions;
import dev.abstratium.core.util.TestDatabaseResetHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TermsAndConditionsResourceTest {

    @Inject
    TestDatabaseResetHelper databaseResetHelper;

    @AfterAll
    void tearDown() {
        // Clean up all test-created terms across every tenant.
        // @AfterAll (rather than @AfterEach) is required because the tenant-isolation
        // tests share state across ordered methods via instance fields.
        databaseResetHelper.resetDatabase();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateAndReadTermsAndConditions() {
        String code = "TEST-TERMS-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode(code);
        terms.setTitle("Test Terms Title");
        terms.setContentEn("Test terms content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .body("code", equalTo(code))
            .body("title", equalTo("Test Terms Title"))
            .body("id", notNullValue())
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("code", equalTo(code))
            .body("title", equalTo("Test Terms Title"));

        given()
            .when()
            .get("/api/terms-and-conditions/code/" + code)
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class))
            .body("size()", equalTo(1))
            .body("[0].id", equalTo(id))
            .body("[0].code", equalTo(code));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListAllTermsAndConditions() {
        String code = "LIST-TEST-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(code);
        terms.setTitle("List Test Title");
        terms.setContentEn("List test content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        // The list must contain the row we just created
        given()
            .when()
            .get("/api/terms-and-conditions")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class))
            .body("code", hasItem(code));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentTerms() {
        given()
            .when()
            .get("/api/terms-and-conditions/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectDuplicateCodeWithOverlappingDates() {
        String code = "DUP-TERMS-" + System.currentTimeMillis();

        // First term: Jan 1 – Jun 30, 2024
        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode(code);
        terms.setTitle("First Terms");
        terms.setContentEn("First content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        terms.setEffectiveUntil(LocalDate.of(2024, 6, 30));

        given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        // Duplicate term: overlaps because it starts on Jun 15 (before the first ends)
        TermsAndConditions duplicate = new TermsAndConditions();
        duplicate.setCode(code);
        duplicate.setTitle("Duplicate Terms");
        duplicate.setContentEn("Duplicate content");
        duplicate.setCurrentVersion("2.0");
        duplicate.setEffectiveFrom(LocalDate.of(2024, 6, 15));
        duplicate.setEffectiveUntil(LocalDate.of(2024, 12, 31));

        given()
            .contentType(ContentType.JSON)
            .body(duplicate)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateTermsAndConditions() {
        String code = "UPDATE-TEST-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode(code);
        terms.setTitle("Original Title");
        terms.setContentEn("Original content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        TermsAndConditions update = new TermsAndConditions();
        update.setCode(code);
        update.setTitle("Updated Title");
        update.setContentEn("Updated content");
        update.setCurrentVersion("2.0");
        update.setEffectiveFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("title", equalTo("Updated Title"))
            .body("currentVersion", equalTo("2.0"));

        // Re-GET to confirm the update was actually persisted
        given()
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("title", equalTo("Updated Title"))
            .body("contentEn", equalTo("Updated content"))
            .body("currentVersion", equalTo("2.0"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateEffectiveUntilWithoutEffectiveFrom() {
        String code = "UPDATE-NULL-FROM-" + System.currentTimeMillis();
        LocalDate untilDate = LocalDate.of(2025, 12, 31);

        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode(code);
        terms.setTitle("Original Title");
        terms.setContentEn("Original content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());
        terms.setEffectiveUntil(untilDate);

        String id = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .body("effectiveUntil", equalTo("2025-12-31"))
            .extract()
            .path("id");

        TermsAndConditions update = new TermsAndConditions();
        update.setCode(code);
        update.setTitle("Updated Title");
        update.setContentEn("Updated content");
        update.setCurrentVersion("2.0");
        update.setEffectiveFrom(null);
        update.setEffectiveUntil(untilDate);

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("title", equalTo("Updated Title"))
            .body("effectiveFrom", nullValue())
            .body("effectiveUntil", equalTo("2025-12-31"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteTermsAndConditions() {
        String code = "DELETE-TEST-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode(code);
        terms.setTitle("To be deleted");
        terms.setContentEn("Delete me");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .delete("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn400ForNullCode() {
        TermsAndConditions terms = new TermsAndConditions();

        terms.setTitle("No code");
        terms.setContentEn("Content without code");

        given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenUpdatingNonExistentTerms() {
        TermsAndConditions update = new TermsAndConditions();
        update.setCode("NO-SUCH-CODE");
        update.setTitle("Does not exist");

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/terms-and-conditions/non-existent-id-xyz")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenDeletingNonExistentTerms() {
        given()
            .when()
            .delete("/api/terms-and-conditions/non-existent-id-xyz")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentCode() {
        given()
            .when()
            .get("/api/terms-and-conditions/code/NON-EXISTENT-CODE-XYZ")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListDistinctCodesWithLatestTitle() {
        String code = "DISTINCT-CODE-" + System.currentTimeMillis();

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setCode(code);
        t1.setTitle("Old Title");
        t1.setContentEn("Old");
        t1.setCurrentVersion("1.0");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));

        given()
            .contentType(ContentType.JSON)
            .body(t1)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setCode(code);
        t2.setTitle("New Title");
        t2.setContentEn("New");
        t2.setCurrentVersion("2.0");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(null);

        given()
            .contentType(ContentType.JSON)
            .body(t2)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        List<TermsAndConditionsCodeSummary> codes = given()
            .when()
            .get("/api/terms-and-conditions/codes")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class))
            .extract()
            .jsonPath()
            .getList(".", TermsAndConditionsCodeSummary.class);

        assertFalse(codes.isEmpty());
        TermsAndConditionsCodeSummary found = codes.stream()
            .filter(c -> c.getCode().equals(code))
            .findFirst()
            .orElse(null);
        assertNotNull(found);
        assertEquals("New Title", found.getTitle());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldAllowContinuousChainForSameCode() {
        String code = "CHAIN-RES-" + System.currentTimeMillis();

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setCode(code);
        t1.setTitle("First");
        t1.setContentEn("First");
        t1.setCurrentVersion("1.0");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));

        String id1 = given()
            .contentType(ContentType.JSON)
            .body(t1)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setCode(code);
        t2.setTitle("Second");
        t2.setContentEn("Second");
        t2.setCurrentVersion("2.0");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(null);

        String id2 = given()
            .contentType(ContentType.JSON)
            .body(t2)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/terms-and-conditions/code/" + code)
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("[0].id", equalTo(id1))
            .body("[1].id", equalTo(id2));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectGapInChain() {
        String code = "GAP-RES-" + System.currentTimeMillis();

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setCode(code);
        t1.setTitle("First");
        t1.setContentEn("First");
        t1.setCurrentVersion("1.0");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));

        given()
            .contentType(ContentType.JSON)
            .body(t1)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setCode(code);
        t2.setTitle("Second");
        t2.setContentEn("Second");
        t2.setCurrentVersion("2.0");
        t2.setEffectiveFrom(LocalDate.of(2024, 8, 1));
        t2.setEffectiveUntil(null);

        given()
            .contentType(ContentType.JSON)
            .body(t2)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        given()
            .when()
            .get("/api/terms-and-conditions")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"other-role"})
    void shouldRejectUnauthorizedRequests() {
        given()
            .when()
            .get("/api/terms-and-conditions")
            .then()
            .statusCode(403);
    }

    // ==================== Tenant Isolation Tests ====================
    //
    // Each scenario is split into two @Test methods so that @OidcSecurity
    // can set a different orgId per method.  @TestInstance(PER_CLASS) lets
    // the pair share state via instance fields, and @Order guarantees the
    // create-as-tenant-A method runs before the access-as-tenant-B method.

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    private String readIsolationId;
    private String readIsolationCode;
    private String updateIsolationId;
    private String updateIsolationCode;
    private String deleteIsolationId;
    private String deleteIsolationCode;

    // --- Read isolation ---

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_A))
    @Order(1)
    void shouldIsolateTenantsOnRead_createAsTenantA() {
        readIsolationCode = "CROSS-READ-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(readIsolationCode);
        terms.setTitle("Cross-tenant read test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        readIsolationId = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Tenant A can read its own data
        given()
            .when()
            .get("/api/terms-and-conditions/" + readIsolationId)
            .then()
            .statusCode(200)
            .body("code", equalTo(readIsolationCode));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_B))
    @Order(2)
    void shouldIsolateTenantsOnRead_tenantBGets404() {
        assertNotNull(readIsolationId, "createAsTenantA must run first");
        given()
            .when()
            .get("/api/terms-and-conditions/" + readIsolationId)
            .then()
            .statusCode(404);
    }

    // --- Update isolation ---

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_A))
    @Order(3)
    void shouldIsolateTenantsOnUpdate_createAsTenantA() {
        updateIsolationCode = "CROSS-UPDATE-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(updateIsolationCode);
        terms.setTitle("Cross-tenant update test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        updateIsolationId = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_B))
    @Order(4)
    void shouldIsolateTenantsOnUpdate_tenantBGets404() {
        assertNotNull(updateIsolationId, "createAsTenantA must run first");

        TermsAndConditions update = new TermsAndConditions();
        update.setCode(updateIsolationCode);
        update.setTitle("Hacked title");
        update.setContentEn("Hacked content");

        // Tenant B cannot update
        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/terms-and-conditions/" + updateIsolationId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_A))
    @Order(5)
    void shouldIsolateTenantsOnUpdate_tenantADataUntouched() {
        assertNotNull(updateIsolationId, "createAsTenantA must run first");
        given()
            .when()
            .get("/api/terms-and-conditions/" + updateIsolationId)
            .then()
            .statusCode(200)
            .body("title", equalTo("Cross-tenant update test"));
    }

    // --- Delete isolation ---

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_A))
    @Order(6)
    void shouldIsolateTenantsOnDelete_createAsTenantA() {
        deleteIsolationCode = "CROSS-DELETE-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(deleteIsolationCode);
        terms.setTitle("Cross-tenant delete test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        deleteIsolationId = given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_B))
    @Order(7)
    void shouldIsolateTenantsOnDelete_tenantBGets404() {
        assertNotNull(deleteIsolationId, "createAsTenantA must run first");
        given()
            .when()
            .delete("/api/terms-and-conditions/" + deleteIsolationId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = TENANT_A))
    @Order(8)
    void shouldIsolateTenantsOnDelete_tenantADataStillExists() {
        assertNotNull(deleteIsolationId, "createAsTenantA must run first");
        given()
            .when()
            .get("/api/terms-and-conditions/" + deleteIsolationId)
            .then()
            .statusCode(200)
            .body("code", equalTo(deleteIsolationCode));
    }
}
