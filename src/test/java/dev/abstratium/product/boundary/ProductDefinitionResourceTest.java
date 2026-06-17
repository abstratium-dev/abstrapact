package dev.abstratium.product.boundary;

import dev.abstratium.product.boundary.dto.*;
import dev.abstratium.product.entity.PartAttributeDefinition;
import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ProductDefinitionResourceTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateAndReadProductDefinition() {
        String productCode = "TEST-PROD-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("Test Product Description");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());
        definition.setProductValidUntil(LocalDate.now().plusYears(1));

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
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
            .get("/api/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Test Product Description"));

        given()
            .when()
            .get("/api/product-definitions/code/" + productCode)
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
            .get("/api/product-definitions")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentProduct() {
        given()
            .when()
            .get("/api/product-definitions/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectDuplicateProductCode() {
        String productCode = "DUP-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("First Product");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201);

        ProductDefinition duplicate = new ProductDefinition();
        duplicate.setOrganisationId("00000000-0000-0000-0000-000000000000");
        duplicate.setProductCode(productCode);
        duplicate.setDescription("Duplicate Product");
        duplicate.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        duplicate.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(duplicate)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateProductDefinition() {
        String productCode = "UPDATE-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("Original Description");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        ProductDefinition update = new ProductDefinition();
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
        update.setProductCode(productCode);
        update.setDescription("Updated Description");
        update.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        update.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("description", equalTo("Updated Description"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteProductDefinition() {
        String productCode = "DELETE-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("To be deleted");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .delete("/api/product-definitions/" + id)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/product-definitions/" + id)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldFilterByBillingModel() {
        given()
            .when()
            .get("/api/product-definitions/billing-model/FIXED_PRICE")
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
            .post("/api/product-definitions/import/yaml")
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
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("Export Test");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/product-definitions/" + id + "/export/yaml")
            .then()
            .statusCode(200)
            .contentType("application/x-yaml")
            .body(containsString("product_code: " + productCode))
            .body(containsString("description: Export Test"))
            .body(containsString("billing_model: FIXED_PRICE"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentProductCode() {
        given()
            .when()
            .get("/api/product-definitions/code/NON-EXISTENT-CODE-XYZ")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn400ForNullProductCode() {
        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setDescription("No code");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenUpdatingNonExistentProduct() {
        ProductDefinition update = new ProductDefinition();
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
        update.setProductCode("NO-SUCH-CODE");
        update.setDescription("Does not exist");
        update.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        update.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/product-definitions/non-existent-id-xyz")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenDeletingNonExistentProduct() {
        given()
            .when()
            .delete("/api/product-definitions/non-existent-id-xyz")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectDuplicateYamlImport() {
        String productCode = "YAML-DUP-" + System.currentTimeMillis();
        String yaml = "product_code: " + productCode + "\n" +
                      "description: First import\n" +
                      "billing_model: FIXED_PRICE\n" +
                      "valid_from: " + LocalDate.now() + "\n" +
                      "valid_until: null\n";

        given()
            .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)))
            .contentType("application/x-yaml")
            .body(yaml)
            .when()
            .post("/api/product-definitions/import/yaml")
            .then()
            .statusCode(201);

        given()
            .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)))
            .contentType("application/x-yaml")
            .body(yaml)
            .when()
            .post("/api/product-definitions/import/yaml")
            .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForYamlExportOfNonExistentProduct() {
        given()
            .when()
            .get("/api/product-definitions/non-existent-id-xyz/export/yaml")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldImportYamlWithOrganisationIdAndValidUntil() {
        String productCode = "YAML-FULL-" + System.currentTimeMillis();
        String yaml = "organisation_id: " + "00000000-0000-0000-0000-000000000000" + "\n" +
                      "product_code: " + productCode + "\n" +
                      "description: Full YAML import\n" +
                      "billing_model: SUBSCRIPTION\n" +
                      "valid_from: " + LocalDate.now() + "\n" +
                      "valid_until: " + LocalDate.now().plusYears(1) + "\n";

        given()
            .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs("application/x-yaml", ContentType.TEXT)))
            .contentType("application/x-yaml")
            .body(yaml)
            .when()
            .post("/api/product-definitions/import/yaml")
            .then()
            .statusCode(201)
            .body("productCode", equalTo(productCode));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldExportYamlWithValidUntilSet() {
        String productCode = "YAML-EXP-UNTIL-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("Export with until");
        definition.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        definition.setProductValidFrom(LocalDate.now());
        definition.setProductValidUntil(LocalDate.now().plusYears(2));

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/product-definitions/" + id + "/export/yaml")
            .then()
            .statusCode(200)
            .contentType("application/x-yaml")
            .body(containsString("valid_until:"))
            .body(containsString("product_code: " + productCode));
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        given()
            .when()
            .get("/api/product-definitions")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"other-role"})
    void shouldRejectUnauthorizedRequests() {
        given()
            .when()
            .get("/api/product-definitions")
            .then()
            .statusCode(403);
    }

    // ==================== Complete Product with Parts Tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateCompleteProductWithParts() {
        String productCode = "COMPLETE-REST-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Complete Product via REST");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());
        request.setProductValidUntil(LocalDate.now().plusYears(1));

        // Add a part with attributes
        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("REST-PART-001");
        part.setDescription("REST Part");
        part.setUnitPrice(new BigDecimal("99.99"));
        part.setDisplayOrder(1);

        List<PartAttributeRequest> attrs = new ArrayList<>();
        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("COLOR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(true);

        List<PartAttributeAllowedValueRequest> values = new ArrayList<>();
        PartAttributeAllowedValueRequest val1 = new PartAttributeAllowedValueRequest();
        val1.setAllowedValue("RED");
        values.add(val1);
        attr.setAllowedValues(values);
        attrs.add(attr);
        part.setAttributes(attrs);
        parts.add(part);
        request.setParts(parts);

        String id = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Complete Product via REST"))
            .body("id", notNullValue())
            .extract()
            .path("id");

        // Verify parts were created
        given()
            .when()
            .get("/api/product-definitions/" + id + "/parts")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].partCode", equalTo("REST-PART-001"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateCompleteProductWithParts() {
        // First create a product
        String productCode = "UPDATE-COMPLETE-REST-" + System.currentTimeMillis();

        ProductDefinitionRequest createRequest = new ProductDefinitionRequest();
        createRequest.setProductCode(productCode);
        createRequest.setDescription("Original");
        createRequest.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        createRequest.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("ORIGINAL-PART");
        part.setDescription("Original");
        part.setUnitPrice(new BigDecimal("50.00"));
        part.setDisplayOrder(1);
        parts.add(part);
        createRequest.setParts(parts);

        String id = given()
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Now update with new parts
        ProductDefinitionRequest updateRequest = new ProductDefinitionRequest();
        updateRequest.setProductCode(productCode);
        updateRequest.setDescription("Updated");
        updateRequest.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        updateRequest.setProductValidFrom(LocalDate.now());

        List<PartRequest> newParts = new ArrayList<>();
        PartRequest newPart = new PartRequest();
        newPart.setPartCode("UPDATED-PART");
        newPart.setDescription("Updated");
        newPart.setUnitPrice(new BigDecimal("75.00"));
        newPart.setDisplayOrder(1);
        newParts.add(newPart);
        updateRequest.setParts(newParts);

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .when()
            .put("/api/product-definitions/" + id + "/complete")
            .then()
            .statusCode(200)
            .body("description", equalTo("Updated"))
            .body("billingModel", equalTo("SUBSCRIPTION"));

        // Verify parts were replaced
        given()
            .when()
            .get("/api/product-definitions/" + id + "/parts")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].partCode", equalTo("UPDATED-PART"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteCompleteProductWithParts() {
        // Create product with parts
        String productCode = "DELETE-COMPLETE-REST-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("To be deleted");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("DELETE-PART");
        part.setDescription("Part to delete");
        part.setUnitPrice(new BigDecimal("100.00"));
        part.setDisplayOrder(1);
        parts.add(part);
        request.setParts(parts);

        String id = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Get the part ID
        String partId = given()
            .when()
            .get("/api/product-definitions/" + id + "/parts")
            .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        // Delete the complete product
        given()
            .when()
            .delete("/api/product-definitions/" + id + "/complete")
            .then()
            .statusCode(204);

        // Verify product is gone
        given()
            .when()
            .get("/api/product-definitions/" + id)
            .then()
            .statusCode(404);

        // Verify part is also gone (cascade)
        given()
            .when()
            .get("/api/product-definitions/parts/" + partId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenUpdatingNonExistentCompleteProduct() {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("NON-EXISTENT");
        request.setDescription("Does not exist");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .put("/api/product-definitions/non-existent-id-xyz/complete")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectDuplicateProductCodeForCompleteProduct() {
        String productCode = "DUP-COMPLETE-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("First");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201);

        // Try to create duplicate
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectCompleteProductWithoutProductCode() {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setDescription("No code");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(400);
    }

    // ==================== Part Management REST Tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldAddPartToExistingProduct() {
        // Create a product first
        String productCode = "PART-ADD-TEST-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setOrganisationId("00000000-0000-0000-0000-000000000000");
        definition.setProductCode(productCode);
        definition.setDescription("Test Product for Part");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setProductValidFrom(LocalDate.now());

        String productId = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Add a part
        PartDefinition part = new PartDefinition();
        part.setOrganisationId("00000000-0000-0000-0000-000000000000");
        part.setPartCode("ADDED-PART");
        part.setDescription("Added Part");
        part.setUnitPrice(new BigDecimal("25.00"));
        part.setDisplayOrder(1);

        given()
            .contentType(ContentType.JSON)
            .body(part)
            .when()
            .post("/api/product-definitions/" + productId + "/parts")
            .then()
            .statusCode(201)
            .body("partCode", equalTo("ADDED-PART"))
            .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenAddingPartToNonExistentProduct() {
        PartDefinition part = new PartDefinition();
        part.setOrganisationId("00000000-0000-0000-0000-000000000000");
        part.setPartCode("ORPHAN-PART");
        part.setDescription("Orphan Part");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);

        given()
            .contentType(ContentType.JSON)
            .body(part)
            .when()
            .post("/api/product-definitions/non-existent-id/parts")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdatePart() {
        // Create product with part
        String productCode = "PART-UPDATE-REST-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Test");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("UPDATE-ME");
        part.setDescription("Original");
        part.setUnitPrice(new BigDecimal("50.00"));
        part.setDisplayOrder(1);
        parts.add(part);
        request.setParts(parts);

        String productId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String partId = given()
            .when()
            .get("/api/product-definitions/" + productId + "/parts")
            .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        // Update the part
        PartDefinition update = new PartDefinition();
        update.setOrganisationId("00000000-0000-0000-0000-000000000000");
        update.setPartCode("UPDATE-ME");
        update.setDescription("Updated via REST");
        update.setUnitPrice(new BigDecimal("75.00"));
        update.setDisplayOrder(1);

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when()
            .put("/api/product-definitions/parts/" + partId)
            .then()
            .statusCode(200)
            .body("description", equalTo("Updated via REST"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeletePart() {
        // Create product with part
        String productCode = "PART-DELETE-REST-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Test");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("DELETE-ME");
        part.setDescription("To be deleted");
        part.setUnitPrice(new BigDecimal("50.00"));
        part.setDisplayOrder(1);
        parts.add(part);
        request.setParts(parts);

        String productId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String partId = given()
            .when()
            .get("/api/product-definitions/" + productId + "/parts")
            .then()
            .statusCode(200)
            .extract()
            .path("[0].id");

        // Delete the part
        given()
            .when()
            .delete("/api/product-definitions/parts/" + partId)
            .then()
            .statusCode(204);

        // Verify it's gone
        given()
            .when()
            .get("/api/product-definitions/parts/" + partId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentPart() {
        given()
            .when()
            .get("/api/product-definitions/parts/non-existent-part-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenUpdatingNonExistentPart() {
        PartDefinition part = new PartDefinition();
        part.setOrganisationId("00000000-0000-0000-0000-000000000000");
        part.setPartCode("NO-SUCH-PART");
        part.setDescription("Does not exist");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);

        given()
            .contentType(ContentType.JSON)
            .body(part)
            .when()
            .put("/api/product-definitions/parts/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenDeletingNonExistentPart() {
        given()
            .when()
            .delete("/api/product-definitions/parts/non-existent-id")
            .then()
            .statusCode(404);
    }

    // ==================== Get Complete Product Tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldGetCompleteProductWithFullTree() {
        String productCode = "GET-COMPLETE-" + System.currentTimeMillis();

        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Complete Product Test");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        // Build a tree: root -> child -> grandchild
        List<PartRequest> parts = new ArrayList<>();
        PartRequest rootPart = new PartRequest();
        rootPart.setPartCode("ROOT-PART");
        rootPart.setDescription("Root");
        rootPart.setUnitPrice(new BigDecimal("100.00"));
        rootPart.setDisplayOrder(1);

        // Add attribute with allowed values
        List<PartAttributeRequest> attrs = new ArrayList<>();
        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("COLOR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(true);
        attr.setDefaultValue("RED");

        List<PartAttributeAllowedValueRequest> values = new ArrayList<>();
        PartAttributeAllowedValueRequest red = new PartAttributeAllowedValueRequest();
        red.setAllowedValue("RED");
        values.add(red);
        PartAttributeAllowedValueRequest blue = new PartAttributeAllowedValueRequest();
        blue.setAllowedValue("BLUE");
        values.add(blue);
        attr.setAllowedValues(values);
        attrs.add(attr);
        rootPart.setAttributes(attrs);

        // Add child part
        List<PartRequest> childParts = new ArrayList<>();
        PartRequest childPart = new PartRequest();
        childPart.setPartCode("CHILD-PART");
        childPart.setDescription("Child");
        childPart.setUnitPrice(new BigDecimal("50.00"));
        childPart.setDisplayOrder(1);
        childParts.add(childPart);
        rootPart.setChildParts(childParts);

        parts.add(rootPart);
        request.setParts(parts);

        String id = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Get complete product
        given()
            .when()
            .get("/api/product-definitions/" + id + "/complete")
            .then()
            .statusCode(200)
            .body("productCode", equalTo(productCode))
            .body("description", equalTo("Complete Product Test"))
            .body("parts", hasSize(1))
            .body("parts[0].partCode", equalTo("ROOT-PART"))
            .body("parts[0].unitPrice", equalTo(100.00f))
            .body("parts[0].attributes", hasSize(1))
            .body("parts[0].attributes[0].attributeName", equalTo("COLOR"))
            .body("parts[0].attributes[0].allowedValues", hasSize(2))
            .body("parts[0].attributes[0].allowedValues[0].allowedValue", equalTo("RED"))
            .body("parts[0].childParts", hasSize(1))
            .body("parts[0].childParts[0].partCode", equalTo("CHILD-PART"))
            .body("parts[0].childParts[0].unitPrice", equalTo(50.00f));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentCompleteProduct() {
        given()
            .when()
            .get("/api/product-definitions/non-existent-id-xyz/complete")
            .then()
            .statusCode(404);
    }
}
