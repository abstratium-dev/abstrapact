package dev.abstratium.demo.boundary.api;

import dev.abstratium.demo.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Tests for DemoResource covering CRUD operations and error handling.
 */
@QuarkusTest
class DemoResourceTest {

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testGetAllReturnsListOfDemos() {
        given()
            .when()
            .get("/api/demo")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", notNullValue());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testCreateDemo() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/demo")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .extract()
            .path("id");

        // cleanup
        given()
            .when()
            .delete("/api/demo/" + id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testUpdateDemo() {
        // Create first
        String id = given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/demo")
            .then()
            .statusCode(200)
            .extract()
            .path("id");

        // Update it
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": \"" + id + "\"}")
            .when()
            .put("/api/demo")
            .then()
            .statusCode(200)
            .body("id", is(id));

        // cleanup
        given()
            .when()
            .delete("/api/demo/" + id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testDeleteNonExistentDemoIsIdempotent() {
        given()
            .when()
            .delete("/api/demo/non-existent-id-that-does-not-exist")
            .then()
            .statusCode(204);
    }

    @Test
    void testGetAllRequiresAuthentication() {
        given()
            .when()
            .get("/api/demo")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testErrorEndpointReturnsRFC7807ProblemDetails() {
        given()
            .when()
            .get("/api/demo/error")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", notNullValue())
            .body("title", is("Demo error for testing"))
            .body("status", is(400))
            .body("detail", containsString("RFC 7807 Problem Details"));
    }

    @Test
    void testErrorEndpointRequiresAuthentication() {
        given()
            .when()
            .get("/api/demo/error")
            .then()
            .statusCode(400);
    }
}
