package dev.abstratium.non_multitenancy.contract.boundary;

import dev.abstratium.non_multitenancy.contract.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.non_multitenancy.contract.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.service.ProductDefinitionService;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
class NonMultitenancyCustomerContractResourceTest {

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() {
        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("REST-CONTRACT-PROD-001");
        pd.setDescription("REST Contract Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(pd);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private CreateCustomerContractRequest buildRequest(String ref) {
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode("REST-CONTRACT-PROD-001");
        li.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference(ref);
        req.setLineItems(List.of(li));
        return req;
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateContractAndReturn201() {
        given()
            .contentType("application/json")
            .body(buildRequest("REST-REF-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("state", equalTo("DRAFT"))
            .body("sellerOrganisationId", equalTo(defaultOrgId))
            .body("lineItems.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn400WhenNoLineItems() {
        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference("NO-LINES");

        given()
            .contentType("application/json")
            .body(req)
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn422WhenProductNotFound() {
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode("DOES-NOT-EXIST");

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference("BAD-CODE");
        req.setLineItems(List.of(li));

        given()
            .contentType("application/json")
            .body(req)
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListContractsForCaller() {
        given()
            .contentType("application/json")
            .body(buildRequest("REST-LIST-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201);

        given()
            .when()
            .get("/api/public/contracts")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldGetContractById() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-GET-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/public/contracts/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("state", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldOfferContract() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-OFFER-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .when()
            .get("/api/public/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("OFFERED"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldAcceptOfferedContract() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-ACCEPT-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json").post("/api/public/contracts/" + id + "/offer").then().statusCode(200);

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        given()
            .when()
            .get("/api/public/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("ACCEPTED"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteLineItemFromDraftContract() {
        String response = given()
            .contentType("application/json")
            .body(buildRequest("REST-DEL-LI-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/contracts")
            .then()
            .statusCode(201)
            .extract()
            .asString();

        io.restassured.path.json.JsonPath jp = new io.restassured.path.json.JsonPath(response);
        String contractId = jp.getString("id");
        String lineItemId = jp.getString("lineItems[0].id");

        given()
            .when()
            .delete("/api/public/contracts/" + contractId + "/line-items/" + lineItemId)
            .then()
            .statusCode(200)
            .body("lineItems.size()", equalTo(0));
    }
}
