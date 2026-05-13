package dev.abstratium.core.boundary.publik;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test for ConfigResource to verify:
 * 1. The endpoint returns the expected config
 * 2. The endpoint is publicly accessible (no authentication required)
 * 3. The endpoint is NOT tracked by OIDC (no @PermitAll annotation needed)
 */
@QuarkusTest
class ConfigResourceTest {

    @Test
    void testConfigEndpointReturnsLogLevel() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("logLevel", notNullValue())
            .body("logLevel", is("INFO")) // Default value from application.properties
            .body("warningMessage", notNullValue()) // Default "-" means no banner
            .body("stage", notNullValue()); // Default "dev" or from test config
    }

    @Test
    void testConfigEndpointIsPubliclyAccessible() {
        // This test verifies that the endpoint can be accessed without authentication
        // The endpoint should NOT have @PermitAll annotation because it's under /public/*
        // which is configured as public in application.properties
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    void testConfigEndpointNotTrackedByOIDC() {
        // Verify that accessing this endpoint doesn't trigger OIDC authentication
        // If OIDC was tracking this endpoint, it would return 302 redirect or 401
        // Instead, it should return 200 with the config data
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200); // Not 302 (redirect) or 401 (unauthorized)
    }

    @Test
    void testConfigEndpointExposesBaselineBuildTimestamp() {
        // Verify that the baseline build timestamp is exposed
        // This allows tracking which version of the baseline is deployed
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("baselineBuildTimestamp", notNullValue())
            // Verify it matches ISO-8601 format (basic check for 'T' and 'Z')
            .body("baselineBuildTimestamp", org.hamcrest.Matchers.containsString("T"))
            .body("baselineBuildTimestamp", org.hamcrest.Matchers.endsWith("Z"));
    }

    @Test
    void testConfigEndpointReturnsWarningMessage() {
        // Verify that warningMessage is returned (default "-" means no banner)
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("warningMessage", notNullValue())
            .body("warningMessage", is("-")); // Default "-" means no banner
    }

    @Test
    void testConfigEndpointReturnsStage() {
        // Verify that stage is returned (test profile uses "test")
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("stage", notNullValue())
            .body("stage", is("test")); // Test profile uses "test"
    }
}
