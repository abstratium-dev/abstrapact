package dev.abstratium.abstrapact.non_multitenancy.sales.boundary;

import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.service.ProductDefinitionService;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
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

    @Inject
    EntityManager em;

    @Inject
    UserTransaction utx;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    /**
     * A second seller organisation id used for cross-org rejection tests.
     * It is a valid UUID shape but distinct from the default org so that
     * product codes prefixed with it cannot resolve to the default org.
     */
    private static final String OTHER_ORG_ID = "11111111-0000-0000-0000-000000000000";

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() throws Exception {
        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("REST-CONTRACT-PROD-001");
        pd.setDescription("REST Contract Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(pd);

        // A second allowed product in the default org, used for PUT update tests.
        ProductDefinition pd2 = new ProductDefinition();
        pd2.setId(UUID.randomUUID().toString());
        pd2.setProductCode("REST-CONTRACT-PROD-002");
        pd2.setDescription("REST Contract Test Product 2");
        pd2.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd2.setProductValidFrom(LocalDate.now());
        pd2.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(pd2);

        // A product that is NOT allowed via the cross-tenant API.
        ProductDefinition disallowed = new ProductDefinition();
        disallowed.setId(UUID.randomUUID().toString());
        disallowed.setProductCode("REST-CONTRACT-PROD-DISALLOWED");
        disallowed.setDescription("REST Contract Test Product - cross-tenant disallowed");
        disallowed.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        disallowed.setProductValidFrom(LocalDate.now());
        disallowed.setCrossTenantApiAllowed(false);
        productDefinitionService.createProductDefinition(disallowed);

        // A product physically stored in a different org, inserted via native SQL
        // so that Hibernate's tenant discriminator does not rewrite the org_id.
        // Used to test that product codes from a different org are rejected.
        String otherProductId = UUID.randomUUID().toString();
        String otherProductCode = OrgScopedCodec.encode(OTHER_ORG_ID, "REST-CONTRACT-PROD-OTHER-ORG", "Product");
        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_product_definition " +
                    "(id, organisation_id, product_code, description, billing_model, " +
                    " product_valid_from, cross_tenant_api_allowed) " +
                    "VALUES (:id, :orgId, :code, :desc, 'FIXED_PRICE', :validFrom, true)")
                .setParameter("id", otherProductId)
                .setParameter("orgId", OTHER_ORG_ID)
                .setParameter("code", otherProductCode)
                .setParameter("desc", "Product in another org")
                .setParameter("validFrom", java.sql.Date.valueOf(LocalDate.now()))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
        // The TestDataCleaner uses tenant-scoped queries and cannot see the
        // product inserted into OTHER_ORG_ID via native SQL. Remove it directly.
        utx.begin();
        try {
            em.createNativeQuery(
                    "DELETE FROM T_product_definition WHERE organisation_id = :orgId")
                .setParameter("orgId", OTHER_ORG_ID)
                .executeUpdate();
            // Also remove any contracts/account roles inserted natively for the
            // account-scoping test (owned by "testuser", not by the current caller).
            em.createNativeQuery(
                    "DELETE FROM T_contract_account_role WHERE account_id = 'testuser'")
                .executeUpdate();
            em.createNativeQuery(
                    "DELETE FROM T_contract WHERE organisation_id = :orgId " +
                    "AND contract_reference LIKE 'REST-SCOPING-%'")
                .setParameter("orgId", defaultOrgId)
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }
    }

    private CreateCustomerContractRequest buildRequest(String ref) {
        return buildRequest(ref, "REST-CONTRACT-PROD-001");
    }

    private CreateCustomerContractRequest buildRequest(String ref, String rawProductCode) {
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode(rawProductCode);
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
            .post("/api/public/sales/contracts")
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
            .post("/api/public/sales/contracts")
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
            .post("/api/public/sales/contracts")
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
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201);

        given()
            .when()
            .get("/api/public/sales/contracts")
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
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
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
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("OFFERED"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldAcceptOfferedContractAndAutoApprove() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-ACCEPT-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json").post("/api/public/sales/contracts/" + id + "/offer").then().statusCode(200);

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        // After acceptance, auto-approval moves the contract to APPROVED.
        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("APPROVED"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteLineItemFromDraftContract() {
        String response = given()
            .contentType("application/json")
            .body(buildRequest("REST-DEL-LI-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .asString();

        io.restassured.path.json.JsonPath jp = new io.restassured.path.json.JsonPath(response);
        String contractId = jp.getString("id");
        String lineItemId = jp.getString("lineItems[0].id");

        given()
            .when()
            .delete("/api/public/sales/contracts/" + contractId + "/line-items/" + lineItemId)
            .then()
            .statusCode(200)
            .body("lineItems.size()", equalTo(0));
    }

    // ==================== Organisation resolution (boundary) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn422WhenProductsFromDifferentOrgs() {
        // The resource prefixes all raw product codes with the supplied orgId.
        // Supplying an orgId that does not own the products causes the prefixed
        // codes to not resolve, which the org-resolution service rejects with 422.
        CustomerLineItemRequest li1 = new CustomerLineItemRequest();
        li1.setProductCode("REST-CONTRACT-PROD-001");
        li1.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(OTHER_ORG_ID);
        req.setContractReference("REST-MIXED-ORG-" + System.currentTimeMillis());
        req.setLineItems(List.of(li1));

        given()
            .contentType("application/json")
            .body(req)
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn422WhenProductNotCrossTenantAllowed() {
        given()
            .contentType("application/json")
            .body(buildRequest("REST-DISALLOWED-" + System.currentTimeMillis(),
                "REST-CONTRACT-PROD-DISALLOWED"))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(422);
    }

    // ==================== Update draft (PUT) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldUpdateDraftContractAndReplaceLineItems() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-PUT-ORIG-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Build an update request with a new contract reference and the second product.
        CustomerLineItemRequest newLi = new CustomerLineItemRequest();
        newLi.setProductCode("REST-CONTRACT-PROD-002");
        newLi.setDisplayOrder(0);

        CreateCustomerContractRequest updateReq = new CreateCustomerContractRequest();
        updateReq.setOrgId(defaultOrgId);
        updateReq.setContractReference("REST-PUT-UPDATED-" + System.currentTimeMillis());
        updateReq.setLineItems(List.of(newLi));

        given()
            .contentType("application/json")
            .body(updateReq)
            .when()
            .put("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("state", equalTo("DRAFT"))
            .body("contractReference", equalTo(updateReq.getContractReference()))
            .body("lineItems.size()", equalTo(1));

        // Verify the update persisted.
        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("contractReference", equalTo(updateReq.getContractReference()))
            .body("lineItems.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectUpdateWhenContractNotDraft() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-PUT-OFFERED-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .body(buildRequest("REST-PUT-AFTER-OFFER-" + System.currentTimeMillis()))
            .when()
            .put("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(422);
    }

    // ==================== Invalid state transitions (boundary) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectAcceptOfDraftContract() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-ACCEPT-DRAFT-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Contract is still DRAFT; accepting must fail with 422.
        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(422);

        // Contract must still be DRAFT.
        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldRejectOfferOfApprovedContract() {
        String id = given()
            .contentType("application/json")
            .body(buildRequest("REST-OFFER-APPROVED-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        // Contract is now APPROVED (auto-approval); offering again must fail with 422.
        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(422);

        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("APPROVED"));
    }

    // ==================== List with orgId filter (boundary) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldFilterContractsByOrgId() {
        String ref = "REST-LIST-FILTER-" + System.currentTimeMillis();
        given()
            .contentType("application/json")
            .body(buildRequest(ref))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201);

        // Filtering by the seller orgId must include the contract.
        given()
            .when()
            .get("/api/public/sales/contracts?orgId=" + defaultOrgId)
            .then()
            .statusCode(200)
            .body("contractReference", hasItem(ref));

        // Filtering by a different orgId must NOT include the contract.
        given()
            .when()
            .get("/api/public/sales/contracts?orgId=" + OTHER_ORG_ID)
            .then()
            .statusCode(200)
            .body("contractReference", not(hasItem(ref)));
    }

    // ==================== Account scoping (boundary) ====================

    @Test
    @TestSecurity(user = "other-customer", roles = {"abstratium-abstrapact_user"})
    void shouldForbidGetOfOtherCustomersContract() throws Exception {
        // Insert a contract owned by "testuser" via native SQL (bypassing the
        // REST resource, which would use the current identity "other-customer").
        String contractId = UUID.randomUUID().toString();
        String ref = "REST-SCOPING-" + System.currentTimeMillis();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_contract " +
                    "(id, organisation_id, contract_reference, contract_date, currency, " +
                    " grand_total, payment_model, state, public_notes, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :ref, :date, 'EUR', 0, 'PREPAID', 'DRAFT', 'notes', :now, :now)")
                .setParameter("id", contractId)
                .setParameter("orgId", defaultOrgId)
                .setParameter("ref", ref)
                .setParameter("date", java.sql.Date.valueOf(LocalDate.now()))
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_contract_account_role " +
                    "(id, organisation_id, contract_id, account_id, role_type) " +
                    "VALUES (:id, :orgId, :contractId, 'testuser', 'CUSTOMER')")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("orgId", defaultOrgId)
                .setParameter("contractId", contractId)
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        // GET as "other-customer" must return 403.
        given()
            .when()
            .get("/api/public/sales/contracts/" + contractId)
            .then()
            .statusCode(403);

        // LIST as "other-customer" must not include the contract.
        given()
            .when()
            .get("/api/public/sales/contracts")
            .then()
            .statusCode(200)
            .body("contractReference", not(hasItem(ref)));
    }
}
