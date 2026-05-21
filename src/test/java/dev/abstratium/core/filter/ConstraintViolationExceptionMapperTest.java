package dev.abstratium.core.filter;

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
    @TestSecurity(user = "testuser", roles = {"user"})
    void testMySqlDuplicateEntryReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/mysql-duplicate")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("A test with name 'temp' already exists. Please choose a different name."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testH2UniqueViolationReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/h2-unique")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("A test name with the provided value already exists. Please choose a different value."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testForeignKeyChildViolationReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/fk-violation")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", is("The referenced resource does not exist. Please ensure all related resources are created first."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testForeignKeyParentViolationReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/fk-parent-violation")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", is("This example is still referenced by other data and cannot be deleted. Remove the dependent data first."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testForeignKeyByErrorCodeReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/fk-by-error-code")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", is("This resource is still referenced by other data and cannot be deleted or modified."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testUqConstraintNameOnlyReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/uq-name-only")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("A resource with the provided value already exists. Please choose a different value."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testNullMessageFallsBackToCauseDetail() {
        given()
            .when()
            .get("/api/test/constraint/null-message")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Duplicate entry detected"))
            .body("detail", is("A test with name 'x' already exists. Please choose a different name."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testNullCauseWithFkConstraintReturnsConflict() {
        given()
            .when()
            .get("/api/test/constraint/null-cause")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", is("This resource is still referenced by other data and cannot be deleted or modified."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testUnknownConstraintViolationReturnsInternalServerError() {
        given()
            .when()
            .get("/api/test/constraint/unknown-constraint")
            .then()
            .statusCode(500)
            .contentType(containsString("problem+json"))
            .body("status", is(500))
            .body("title", is("Database operation failed"))
            .body("detail", is("A resource with the provided value already exists. Please choose a different value."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testFkParentViolationWithReferencesStyleMessage() {
        given()
            .when()
            .get("/api/test/constraint/fk-parent-references-style")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", containsString("example"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testFkParentViolationWithTUnderscoreFallback() {
        given()
            .when()
            .get("/api/test/constraint/fk-parent-t-fallback")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", containsString("example"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testFkParentViolationResolvesEntityFromConstraintName() {
        given()
            .when()
            .get("/api/test/constraint/fk-constraint-name-only")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", containsString("order"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testFkChildViolationFromCauseMessage() {
        given()
            .when()
            .get("/api/test/constraint/fk-cause-message")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is("Resource is still referenced by other data"))
            .body("detail", is("The referenced resource does not exist. Please ensure all related resources are created first."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testDuplicateEntryWithoutKeyPartReturnsValueDetail() {
        given()
            .when()
            .get("/api/test/constraint/duplicate-no-key")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("detail", is("The value 'abc' already exists. Please choose a different value."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testH2UniqueViolationWithNullConstraintName() {
        given()
            .when()
            .get("/api/test/constraint/h2-unique-null-constraint")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("detail", is("A resource with the provided value already exists. Please choose a different value."));
    }
}
