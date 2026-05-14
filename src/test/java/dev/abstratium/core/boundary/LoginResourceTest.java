package dev.abstratium.core.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for the LoginResource endpoint.
 * 
 * This endpoint triggers OIDC authentication and redirects to the home page.
 */
@QuarkusTest
class LoginResourceTest {

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {})
    void testLoginRedirectsToHomePage() {
        // When authenticated (via @TestSecurity), the endpoint should redirect to home page
        // The Location header will be an absolute URI (e.g., http://localhost:8088/)
        RestAssured.given()
            .redirects().follow(false)
            .when()
            .get("/api/auth/login")
            .then()
            .statusCode(303)
            .header("Location", equalTo("http://localhost:10088/"));
    }
}
