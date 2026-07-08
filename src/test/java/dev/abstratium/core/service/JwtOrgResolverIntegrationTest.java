package dev.abstratium.core.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests verifying that JwtOrgResolver.resolveTenantId() correctly
 * parses the orgId from a Bearer token's JWT payload.
 *
 * These tests use a hand-crafted (unsigned) JWT since JwtOrgResolver only
 * inspects the payload — it does not verify the signature.
 * The token is placed in the Authorization header; @TestSecurity still handles
 * the security layer for the endpoint, but the raw header is present and
 * parseable by JwtOrgResolver.
 */
@QuarkusTest
class JwtOrgResolverIntegrationTest {

    @Inject
    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String buildBearerToken(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"PS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".fakesig";
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    void resolveTenantId_withOrgIdInBearer_usesOrgId() {
        String token = buildBearerToken(
                "{\"sub\":\"testuser\",\"orgId\":\"" + defaultOrgId + "\",\"groups\":[\"jwt-test-user\"]}");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + defaultOrgId + "\""));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    void resolveTenantId_withNoOrgIdInBearer_usesDefault() {
        String token = buildBearerToken(
                "{\"sub\":\"testuser\",\"groups\":[\"jwt-test-user\"]}");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + defaultOrgId + "\""));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    void resolveTenantId_withMalformedBearer_usesDefault() {
        given()
            .header("Authorization", "Bearer not.a.valid.jwt.with.too.many.parts.here")
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + defaultOrgId + "\""));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"jwt-test-user"})
    void resolveTenantId_withBearerHavingBlankOrgId_usesDefault() {
        String token = buildBearerToken(
                "{\"sub\":\"testuser\",\"orgId\":\"\",\"groups\":[\"jwt-test-user\"]}");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/test/jwt-org")
            .then()
            .statusCode(200)
            .body(is("\"" + defaultOrgId + "\""));
    }
}
