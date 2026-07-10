package dev.abstratium.product.service;

import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the product_code column accepts prefixed codes ({orgId}::{rawCode})
 * and that new products stored with the prefixed format can be retrieved correctly.
 *
 * The V01.021 migration updates any un-prefixed rows, so these tests confirm the
 * column can hold prefixed values and that the format round-trips correctly.
 */
@QuarkusTest
class ProductCodePrefixMigrationTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void prefixedProductCodeIsStoredAndRetrieved() {
        String orgId = "00000000-0000-0000-0000-000000000000";
        String rawCode = "PROD-PREFIX-" + System.currentTimeMillis();
        String prefixedCode = orgId + "::" + rawCode;

        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode(prefixedCode);
        pd.setDescription("Prefix test");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        em.persist(pd);
        em.flush();
        em.clear();

        ProductDefinition found = em.find(ProductDefinition.class, pd.getId());
        assertNotNull(found);
        assertEquals(prefixedCode, found.getProductCode());
        assertTrue(found.getProductCode().contains("::"));
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void prefixedCodeLengthFitsColumn() {
        String orgId = "12345678-1234-1234-1234-123456789012";
        String rawCode = "LONG-CODE-" + System.currentTimeMillis();
        String prefixedCode = orgId + "::" + rawCode;

        assertTrue(prefixedCode.length() <= 100,
            "Prefixed code must fit in the VARCHAR(100) column but was: " + prefixedCode.length());

        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode(prefixedCode);
        pd.setDescription("Column width test");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        em.persist(pd);
        em.flush();

        ProductDefinition found = em.find(ProductDefinition.class, pd.getId());
        assertNotNull(found);
        assertEquals(prefixedCode, found.getProductCode());
    }

    @Test
    void prefixedCodeColumnWidthIsEnoughForAnyUuidAndCode() {
        // UUID is 36 chars, '::' is 2, max raw code is 50 chars => 88 total, well under 100
        String uuid = "12345678-1234-1234-1234-123456789012";
        String rawCode = "A".repeat(50);
        String prefixedCode = uuid + "::" + rawCode;
        assertEquals(88, prefixedCode.length());
        assertTrue(prefixedCode.length() <= 100);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void twoProductsWithDifferentPrefixedCodesAreBothStorable() {
        String rawCode = "SAME-RAW-" + System.currentTimeMillis();
        String org1 = "aaaaaaaa-0000-0000-0000-000000000001";
        String org2 = "bbbbbbbb-0000-0000-0000-000000000002";

        String id1 = given()
            .contentType(ContentType.JSON)
            .body(productDefJson(org1 + "::" + rawCode))
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String id2 = given()
            .contentType(ContentType.JSON)
            .body(productDefJson(org2 + "::" + rawCode))
            .when()
            .post("/api/product-definitions")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        assertNotEquals(id1, id2);
    }

    private ProductDefinition productDefJson(String productCode) {
        ProductDefinition pd = new ProductDefinition();
        pd.setProductCode(productCode);
        pd.setDescription("Multi-org test");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        return pd;
    }
}
