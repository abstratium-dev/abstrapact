package dev.abstratium.conditions.boundary;

import dev.abstratium.conditions.entity.TermsAndConditions;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class TermsAndConditionsResourceTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateAndReadTermsAndConditions() {
        String code = "TEST-TERMS-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        given()
            .when()
            .get("/api/terms-and-conditions")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
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

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
        terms.setCode(code);
        terms.setTitle("First Terms");
        terms.setContentEn("First content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201);

        TermsAndConditions duplicate = new TermsAndConditions();
        duplicate.setOrganisationId("00000000-0000-0000-0000-000000000000");
        duplicate.setCode(code);
        duplicate.setTitle("Duplicate Terms");
        duplicate.setContentEn("Duplicate content");
        duplicate.setCurrentVersion("2.0");
        duplicate.setEffectiveFrom(LocalDate.now());

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
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateEffectiveUntilWithoutEffectiveFrom() {
        String code = "UPDATE-NULL-FROM-" + System.currentTimeMillis();
        LocalDate untilDate = LocalDate.of(2025, 12, 31);

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        terms.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
    void shouldAllowContinuousChainForSameCode() {
        String code = "CHAIN-RES-" + System.currentTimeMillis();

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        t2.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        t1.setOrganisationId("00000000-0000-0000-0000-000000000000");
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
        t2.setOrganisationId("00000000-0000-0000-0000-000000000000");
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

    // ==================== Cross-Tenant Isolation Tests ====================

    private String buildBearerToken(String orgId) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"PS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"testuser\",\"orgId\":\"" + orgId + "\",\"groups\":[\"abstratium-abstrapact_user\"]}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fakesig";
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldIsolateTenantsOnRead() {
        String code = "CROSS-READ-" + System.currentTimeMillis();
        String tenantA = "11111111-1111-1111-1111-111111111111";
        String tenantB = "22222222-2222-2222-2222-222222222222";

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(code);
        terms.setTitle("Cross-tenant read test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Tenant A can read
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("code", equalTo(code));

        // Tenant B cannot read
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantB))
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldIsolateTenantsOnUpdate() {
        String code = "CROSS-UPDATE-" + System.currentTimeMillis();
        String tenantA = "11111111-1111-1111-1111-111111111111";
        String tenantB = "22222222-2222-2222-2222-222222222222";

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(code);
        terms.setTitle("Cross-tenant update test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
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
        update.setTitle("Hacked title");
        update.setContentEn("Hacked content");

        // Tenant B cannot update
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantB))
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(404);

        // Verify tenant A's data is untouched
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("title", equalTo("Cross-tenant update test"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldIsolateTenantsOnDelete() {
        String code = "CROSS-DELETE-" + System.currentTimeMillis();
        String tenantA = "11111111-1111-1111-1111-111111111111";
        String tenantB = "22222222-2222-2222-2222-222222222222";

        TermsAndConditions terms = new TermsAndConditions();
        terms.setCode(code);
        terms.setTitle("Cross-tenant delete test");
        terms.setContentEn("Content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        String id = given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
            .contentType(ContentType.JSON)
            .body(terms)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Tenant B cannot delete
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantB))
            .when()
            .delete("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(404);

        // Verify tenant A's data still exists
        given()
            .header("Authorization", "Bearer " + buildBearerToken(tenantA))
            .when()
            .get("/api/terms-and-conditions/" + id)
            .then()
            .statusCode(200)
            .body("code", equalTo(code));
    }
}
