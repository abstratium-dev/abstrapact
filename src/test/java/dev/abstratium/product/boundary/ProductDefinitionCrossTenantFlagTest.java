package dev.abstratium.product.boundary;

import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class ProductDefinitionCrossTenantFlagTest {

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void crossTenantApiAllowedDefaultsToFalse() {
        String productCode = "CROSS-TENANT-DEFAULT-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode(productCode);
        definition.setDescription("Cross tenant test product");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .body("crossTenantApiAllowed", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void canSetCrossTenantApiAllowedToTrue() {
        String productCode = "CROSS-TENANT-ENABLED-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode(productCode);
        definition.setDescription("Cross tenant enabled product");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        definition.setCrossTenantApiAllowed(true);

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .body("crossTenantApiAllowed", equalTo(true))
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("crossTenantApiAllowed", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void canUpdateCrossTenantApiAllowedFlag() {
        String productCode = "CROSS-TENANT-UPDATE-" + System.currentTimeMillis();

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode(productCode);
        definition.setDescription("Update test product");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        definition.setCrossTenantApiAllowed(false);

        String id = given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .body("crossTenantApiAllowed", equalTo(false))
            .extract()
            .path("id");

        definition.setCrossTenantApiAllowed(true);

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .put("/api/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("crossTenantApiAllowed", equalTo(true));

        given()
            .when()
            .get("/api/product-definitions/" + id)
            .then()
            .statusCode(200)
            .body("crossTenantApiAllowed", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void productCodeColumnAcceptsLongPrefixedCode() {
        String orgId = "00000000-0000-0000-0000-000000000001";
        String rawCode = "PROD-WITH-VERY-LONG-CODE-001";
        String prefixedCode = orgId + "::" + rawCode;

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode(prefixedCode);
        definition.setDescription("Prefixed code test");
        definition.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        definition.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);

        given()
            .contentType(ContentType.JSON)
            .body(definition)
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .body("productCode", equalTo(prefixedCode))
            .body("id", notNullValue());
    }
}
