package dev.abstratium.core.filter;

import dev.abstratium.demo.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests for ConstraintViolationExceptionMapper verifying that Hibernate
 * ConstraintViolationException is mapped to RFC 7807 Problem Details.
 *
 * Uses a test-only JAX-RS resource ({@link ConstraintViolationTestResource})
 * that throws the exceptions directly, avoiding any dependency on demo
 * entities or database tables.
 */
@QuarkusTest
class ConstraintViolationExceptionMapperTest {

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testMySqlDuplicateEntryReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/mysql-duplicate")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("could not execute statement"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testH2UniqueViolationReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/h2-unique")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("could not execute statement"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testForeignKeyViolationReturnsInternalServerError() {
        given()
            .when()
            .get("/api/test/constraint/fk-violation")
            .then()
            .statusCode(500)
            .contentType(containsString("problem+json"))
            .body("status", is(500))
            .body("title", is("Database operation failed"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testUqConstraintNameOnlyReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/uq-name-only")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testNullMessageFallsBackToCauseDetail() {
        given()
            .when()
            .get("/api/test/constraint/null-message")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", containsString("Duplicate entry"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testNullCauseReturnsDefaultDetail() {
        given()
            .when()
            .get("/api/test/constraint/null-cause")
            .then()
            .statusCode(500)
            .contentType(containsString("problem+json"))
            .body("status", is(500))
            .body("title", is("Database operation failed"))
            .body("detail", is("A database constraint was violated."));
    }
}
