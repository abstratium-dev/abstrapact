package dev.abstratium.contracts.boundary;

import dev.abstratium.contracts.boundary.dto.CreateDraftContractRequest;
import dev.abstratium.contracts.boundary.dto.LineItemRequest;
import dev.abstratium.contracts.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.product.boundary.dto.PartAttributeRequest;
import dev.abstratium.product.boundary.dto.PartRequest;
import dev.abstratium.product.boundary.dto.ProductDefinitionRequest;
import dev.abstratium.product.entity.PartAttributeDefinition;
import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ContractResourceTest {

    private String createProductDefinitionWithPart(String productCode, ProductDefinition.PaymentModel paymentModel) {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Test product for contract");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setPaymentModel(paymentModel);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PART-" + productCode);
        part.setDescription("A part");
        part.setUnitPrice(new BigDecimal("100.00"));
        part.setDisplayOrder(1);

        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("COLOR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(false);
        part.setAttributes(List.of(attr));

        request.setParts(List.of(part));

        return given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateDraftContractWithLineItem() {
        String productDefId = createProductDefinitionWithPart("CONTRACT-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.PREPAID);

        PartInstanceAttributeRequest attrReq = new PartInstanceAttributeRequest();
        attrReq.setAttributeName("COLOR");
        attrReq.setAttributeValue("BLUE");

        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId(productDefId);
        lineItem.setDisplayOrder(1);
        lineItem.setAttributes(List.of(attrReq));

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("REF-" + System.currentTimeMillis());
        request.setPublicNotes("Draft for customer review");
        request.setLineItems(List.of(lineItem));

        String contractId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("contractReference", notNullValue())
            .body("state", equalTo("DRAFT"))
            .body("paymentModel", equalTo("PREPAID"))
            .body("currency", notNullValue())
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/contracts/" + contractId)
            .then()
            .statusCode(200)
            .body("id", equalTo(contractId))
            .body("state", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCalculatePostpaidContractPaymentModel() {
        String productDefId = createProductDefinitionWithPart("POSTPAID-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.POSTPAID);

        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId(productDefId);
        lineItem.setDisplayOrder(1);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("POSTPAID-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("paymentModel", equalTo("POSTPAID"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateDraftContractWithNoLineItems() {
        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("NO-LINE-ITEMS-REF-" + System.currentTimeMillis());

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("state", equalTo("DRAFT"))
            .body("paymentModel", equalTo("PREPAID"))
            .body("grandTotal", equalTo(0));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListContracts() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListContractsByState() {
        given()
            .when()
            .get("/api/contracts/state/DRAFT")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentContract() {
        given()
            .when()
            .get("/api/contracts/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn422WhenProductDefinitionNotFound() {
        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId("non-existent-product-def-id");
        lineItem.setDisplayOrder(1);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("422-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteDraftContract() {
        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("DELETE-REF-" + System.currentTimeMillis());

        String contractId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .delete("/api/contracts/" + contractId)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/contracts/" + contractId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenDeletingNonExistentContract() {
        given()
            .when()
            .delete("/api/contracts/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCalculateGrandTotalFromLineItems() {
        String productDefId = createProductDefinitionWithPart("TOTAL-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.PREPAID);

        LineItemRequest lineItem1 = new LineItemRequest();
        lineItem1.setProductDefinitionId(productDefId);
        lineItem1.setDisplayOrder(1);

        LineItemRequest lineItem2 = new LineItemRequest();
        lineItem2.setProductDefinitionId(productDefId);
        lineItem2.setDisplayOrder(2);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("GRAND-TOTAL-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem1, lineItem2));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("grandTotal", equalTo(200.0f));
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"other-role"})
    void shouldRejectUnauthorizedRequests() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(403);
    }
}
