package dev.abstratium.core.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserInfoResourceTest {

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstracore_user"})
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "testuser@example.com"),
        @Claim(key = "email_verified", value = "true"),
        @Claim(key = "name", value = "Test User"),
        @Claim(key = "jti", value = "test-jti-123"),
        @Claim(key = "auth_method", value = "password"),
        @Claim(key = "iss", value = "https://issuer.example.com"),
        @Claim(key = "sub", value = "test-subject-id"),
        @Claim(key = "aud", value = "abstratium-abstracore"),
        @Claim(key = "upn", value = "testuser@example.com")
    })
    void testGetUserInfoReturnsExpectedClaims() {
        given()
            .when()
            .get("/api/core/userinfo")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("email", is("testuser@example.com"))
            .body("name", is("Test User"))
            .body("jti", is("test-jti-123"))
            .body("auth_method", is("password"))
            .body("isAuthenticated", is(true))
            .body("sub", notNullValue());
    }

    @Test
    @TestSecurity(user = "minimal@example.com", roles = {})
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "minimal@example.com"),
        @Claim(key = "sub", value = "minimal-subject")
    })
    void testGetUserInfoWithMinimalClaims() {
        given()
            .when()
            .get("/api/core/userinfo")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("email", is("minimal@example.com"))
            .body("isAuthenticated", is(true));
    }

    @Test
    void testGetUserInfoRequiresAuthentication() {
        given()
            .when()
            .get("/api/core/userinfo")
            .then()
            .statusCode(anyOf(is(400), is(401), is(403)));
    }
}
