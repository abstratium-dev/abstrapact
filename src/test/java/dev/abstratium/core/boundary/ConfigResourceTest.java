package dev.abstratium.core.boundary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class ConfigResourceTest {

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstrapact_user"})
    void testGetConfig() {
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("organisationId", notNullValue())
            .body("currencyCode", notNullValue())
            .body("locale", notNullValue());
    }

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstrapact_user"})
    void testUpdateConfig() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"currencyCode\": \"USD\", \"locale\": \"de-DE\"}")
            .when()
            .put("/api/config")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("currencyCode", is("USD"))
            .body("locale", is("de-DE"));
    }

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstrapact_user"})
    void testUpdateConfigWithBlankCurrencyCode() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"currencyCode\": \"\", \"locale\": \"de-DE\"}")
            .when()
            .put("/api/config")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstrapact_user"})
    void testUpdateConfigWithBlankLocale() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"currencyCode\": \"USD\", \"locale\": \"\"}")
            .when()
            .put("/api/config")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser@example.com", roles = {"abstratium-abstrapact_user"})
    void testGetConfigAfterUpdate() {
        // Update the config
        given()
            .contentType(ContentType.JSON)
            .body("{\"currencyCode\": \"EUR\", \"locale\": \"fr-CH\"}")
            .when()
            .put("/api/config")
            .then()
            .statusCode(200)
            .body("currencyCode", is("EUR"))
            .body("locale", is("fr-CH"));

        // Verify GET returns updated values
        given()
            .when()
            .get("/api/config")
            .then()
            .statusCode(200)
            .body("currencyCode", is("EUR"))
            .body("locale", is("fr-CH"));
    }
}
