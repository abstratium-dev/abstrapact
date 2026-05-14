package dev.abstratium.core.filter;

import dev.abstratium.demo.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests for IllegalArgumentExceptionMapper verifying that IllegalArgumentException
 * is mapped to RFC 7807 Problem Details with HTTP 400 Bad Request.
 */
@QuarkusTest
class IllegalArgumentExceptionMapperTest {

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testIllegalArgumentExceptionReturnsBadRequest() {
        given()
            .when()
            .get("/api/demo/illegal-argument?value=bad")
            .then()
            .statusCode(400)
            .contentType(containsString("problem+json"))
            .body("status", is(400))
            .body("title", is("Bad Request"))
            .body("detail", containsString("Illegal argument: bad"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {Roles.USER})
    void testIllegalArgumentExceptionDetailContainsMessage() {
        given()
            .when()
            .get("/api/demo/illegal-argument?value=foo")
            .then()
            .statusCode(400)
            .body("detail", containsString("foo"));
    }
}
