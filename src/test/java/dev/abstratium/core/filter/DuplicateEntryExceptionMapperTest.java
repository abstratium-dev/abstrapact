package dev.abstratium.core.filter;

import dev.abstratium.demo.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests for DuplicateEntryExceptionMapper verifying that it walks the
 * exception chain to find Hibernate ConstraintViolationException and
 * maps duplicate-entry violations to HTTP 409 Conflict.
 *
 * Uses a test-only JAX-RS resource ({@link DuplicateEntryTestResource})
 * that throws wrapped exceptions, avoiding any dependency on demo
 * entities or database tables.
 */
@QuarkusTest
class DuplicateEntryExceptionMapperTest {

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testWrappedMySqlDuplicateReturnsConflict() {
        given()
            .when()
            .get("/api/test/duplicate-entry/wrapped-mysql-duplicate")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testWrappedH2UniqueReturnsConflict() {
        given()
            .when()
            .get("/api/test/duplicate-entry/wrapped-h2-unique")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testWrappedFkViolationReturnsInternalServerError() {
        given()
            .when()
            .get("/api/test/duplicate-entry/wrapped-fk-violation")
            .then()
            .statusCode(500)
            .contentType(containsString("problem+json"))
            .body("status", is(500))
            .body("title", is("Internal Server Error"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testPlainExceptionReturnsInternalServerError() {
        given()
            .when()
            .get("/api/test/duplicate-entry/plain-exception")
            .then()
            .statusCode(500)
            .contentType(containsString("problem+json"))
            .body("status", is(500))
            .body("title", is("Internal Server Error"))
            .body("detail", is("something went wrong"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testWrappedNullMessageReturnsConflict() {
        given()
            .when()
            .get("/api/test/duplicate-entry/wrapped-null-message")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", containsString("Duplicate entry"));
    }
}
