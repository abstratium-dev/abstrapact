package dev.abstratium.core.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class FunctionalExceptionTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testErrorCodeConstructorProducesConflict() {
        given()
            .when()
            .get("/api/test/functional-exception/with-error-code")
            .then()
            .statusCode(409)
            .contentType(containsString("problem+json"))
            .body("status", is(409))
            .body("title", is(ErrorCode.DUPLICATE_ENTRY.getDescription()))
            .body("detail", is("thrown with error code"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testTitleConstructorProducesBadRequest() {
        given()
            .when()
            .get("/api/test/functional-exception/with-title")
            .then()
            .statusCode(400)
            .contentType(containsString("problem+json"))
            .body("status", is(400))
            .body("title", is("Custom Title"))
            .body("detail", is("thrown with custom title"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"user"})
    void testTypeUriConstructorProducesNotFound() {
        given()
            .when()
            .get("/api/test/functional-exception/with-type-uri")
            .then()
            .statusCode(404)
            .contentType(containsString("problem+json"))
            .body("status", is(404))
            .body("title", is("Custom Title"))
            .body("detail", is("thrown with type uri"));
    }
}
