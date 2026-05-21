package dev.abstratium.demo.boundary.publik;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class PublicInfoResourceTest {

    @Test
    void testGetInfoReturnsApplicationDetails() {
        given()
            .when()
            .get("/public/info")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("application", is("Abstracore"))
            .body("version", notNullValue())
            .body("description", notNullValue());
    }

    @Test
    void testGetInfoIsAccessibleWithoutAuthentication() {
        given()
            .when()
            .get("/public/info")
            .then()
            .statusCode(200);
    }
}
