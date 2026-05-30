package dev.abstratium.product.boundary;

import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ProductDefinitionResourceTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateAndReadProductDefinition() {
        String productCode = "TEST-PROD-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("test-org");
        definition.setProductCode(productCode);
        definition.setDescription("Test Product Description");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());
        definition.setProductValidUntil(LocalDate.now().plusYears(1));

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(201)
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Test Product Description"))
            .body("billingModel", equalTo("FIXED_PRICE"))
            .body("id", notNullValue())
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/v1/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Test Product Description"));

        given()
            .when()
            .get("/api/v1/product-definitions/code/" + productCode)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("productCode", equalTo(productCode));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListAllProductDefinitions() {
        given()
            .when()
            .get("/api/v1/product-definitions")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentProduct() {
        given()
            .when()
            .get("/api/v1/product-definitions/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectDuplicateProductCode() {
        String productCode = "DUP-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("test-org");
        definition.setProductCode(productCode);
        definition.setDescription("First Product");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(201);

        ProductDefinition duplicate = new ProductDefinition();
        duplicate.setOrganisationId("test-org");
        duplicate.setProductCode(productCode);
        duplicate.setDescription("Duplicate Product");
        duplicate.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        duplicate.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(duplicate)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateProductDefinition() {
        String productCode = "UPDATE-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("test-org");
        definition.setProductCode(productCode);
        definition.setDescription("Original Description");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        ProductDefinition update = new ProductDefinition();
        update.setOrganisationId("test-org");
        update.setProductCode(productCode);
        update.setDescription("Updated Description");
        update.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        update.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/v1/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("description", equalTo("Updated Description"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteProductDefinition() {
        String productCode = "DELETE-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("test-org");
        definition.setProductCode(productCode);
        definition.setDescription("To be deleted");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .delete("/api/v1/product-definitions/" + id)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/v1/product-definitions/" + id)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldFilterByBillingModel() {
        given()
            .when()
            .get("/api/v1/product-definitions/billing-model/FIXED_PRICE")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldImportProductDefinitionFromYaml() {
        String productCode = "YAML-IMPORT-" + System.currentTimeMillis();
        String yaml = "product_code: " + productCode + "\n" +
                      "description: Imported from YAML\n" +
                      "billing_model: SUBSCRIPTION\n" +
                      "valid_from: " + LocalDate.now() + "\n" +
                      "valid_until: null\n";

        given()
            .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)))
            .contentType("application/x-yaml")
            .body(yaml)
            .when()
            .post("/api/v1/product-definitions/import/yaml")
            .then()
            .statusCode(201)
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Imported from YAML"))
            .body("billingModel", equalTo("SUBSCRIPTION"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldExportProductDefinitionToYaml() {
        String productCode = "YAML-EXPORT-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("test-org");
        definition.setProductCode(productCode);
        definition.setDescription("Export Test");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/v1/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/v1/product-definitions/" + id + "/export/yaml")
            .then()
            .statusCode(200)
            .contentType("application/x-yaml")
            .body(containsString("product_code: " + productCode))
            .body(containsString("description: Export Test"))
            .body(containsString("billing_model: FIXED_PRICE"));
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        given()
            .when()
            .get("/api/v1/product-definitions")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"other-role"})
    void shouldRejectUnauthorizedRequests() {
        given()
            .when()
            .get("/api/v1/product-definitions")
            .then()
            .statusCode(403);
    }
}
