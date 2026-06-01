package dev.abstratium.conditions.boundary;

import dev.abstratium.conditions.entity.TermsAndConditions;
import dev.abstratium.core.service.JwtOrgResolver;
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
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        terms.setCode(code);
        terms.setTitle("Test Terms Title");
        terms.setContent("Test terms content");
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
            .body("id", equalTo(id))
            .body("code", equalTo(code));
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
    void shouldRejectDuplicateCode() {
        String code = "DUP-TERMS-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        terms.setCode(code);
        terms.setTitle("First Terms");
        terms.setContent("First content");
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
        duplicate.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        duplicate.setCode(code);
        duplicate.setTitle("Duplicate Terms");
        duplicate.setContent("Duplicate content");
        duplicate.setCurrentVersion("2.0");
        duplicate.setEffectiveFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(duplicate)
            .when()
            .post("/api/terms-and-conditions")
            .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateTermsAndConditions() {
        String code = "UPDATE-TEST-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        terms.setCode(code);
        terms.setTitle("Original Title");
        terms.setContent("Original content");
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
        update.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        update.setCode(code);
        update.setTitle("Updated Title");
        update.setContent("Updated content");
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
    void shouldDeleteTermsAndConditions() {
        String code = "DELETE-TEST-" + System.currentTimeMillis();

        TermsAndConditions terms = new TermsAndConditions();
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        terms.setCode(code);
        terms.setTitle("To be deleted");
        terms.setContent("Delete me");
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
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        terms.setTitle("No code");
        terms.setContent("Content without code");

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
        update.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
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
        terms.setContent("Content");
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
        terms.setContent("Content");
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
        update.setContent("Hacked content");

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
        terms.setContent("Content");
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
