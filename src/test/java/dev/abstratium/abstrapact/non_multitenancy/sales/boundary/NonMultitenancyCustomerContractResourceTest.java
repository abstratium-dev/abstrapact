package dev.abstratium.abstrapact.non_multitenancy.sales.boundary;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.service.NonMultitenancyCustomerContractService;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.service.ProductDefinitionService;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(NonMultitenancyCustomerContractResourceTest.TestProfile.class)
class NonMultitenancyCustomerContractResourceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "abstrapact.payment.stripe.api-base",
                "http://localhost:" + WIREMOCK_PORT,
                "abstrapact.payment.psp", "stripe"
            );
        }
    }

    static final int WIREMOCK_PORT = 19996;
    static WireMockServer wireMock;

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    NonMultitenancyCustomerContractService contractService;

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

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() throws Exception {
        wireMock.resetAll();
        // Stub the Stripe Checkout Session creation endpoint
        wireMock.stubFor(post(urlPathEqualTo("/v1/checkout/sessions"))
            .willReturn(okJson("""
                {
                  "id": "cs_rest_test_123",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/cs_rest_test_123",
                  "payment_status": "unpaid",
                  "status": "open"
                }
                """)));

        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("REST-CONTRACT-PROD-001");
        pd.setDescription("REST Contract Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        pd.setStripeSecretKey("sk_test_rest");
        pd.setStripeWebhookSecret("whsec_test_rest");
        productDefinitionService.createProductDefinition(pd);

        // A second allowed product in the default org, used for PUT update tests.
        ProductDefinition pd2 = new ProductDefinition();
        pd2.setId(UUID.randomUUID().toString());
        pd2.setProductCode("REST-CONTRACT-PROD-002");
        pd2.setDescription("REST Contract Test Product 2");
        pd2.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd2.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        pd2.setProductValidFrom(LocalDate.now());
        pd2.setCrossTenantApiAllowed(true);
        pd2.setStripeSecretKey("sk_test_rest");
        pd2.setStripeWebhookSecret("whsec_test_rest");
        productDefinitionService.createProductDefinition(pd2);

        // A product that is NOT allowed via the cross-tenant API.
        ProductDefinition disallowed = new ProductDefinition();
        disallowed.setId(UUID.randomUUID().toString());
        disallowed.setProductCode("REST-CONTRACT-PROD-DISALLOWED");
        disallowed.setDescription("REST Contract Test Product - cross-tenant disallowed");
        disallowed.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        disallowed.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
    void shouldCreateContractWithMillisecondTimestamp() {
        String ref = "REST-MS-" + System.currentTimeMillis();
        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest(ref))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .body("createdAt", matchesPattern(".*\\.\\d{3,}.*"));
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-ACCEPT-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString()).post("/api/public/sales/contracts/" + id + "/offer").then().statusCode(200);

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        // After acceptance, auto-approval + payment handling moves the contract to
        // AWAITING_PAYMENT (prepaid model with Stripe checkout session created).
        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("AWAITING_PAYMENT"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteLineItemFromDraftContract() {
        String response = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-PUT-OFFERED-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-APPROVED-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        // Contract is now AWAITING_PAYMENT (auto-approval + payment); offering again must fail with 422.
        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(422);

        given()
            .when()
            .get("/api/public/sales/contracts/" + id)
            .then()
            .statusCode(200)
            .body("state", equalTo("AWAITING_PAYMENT"));
    }

    // ==================== Idempotency negative tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithoutIdempotencyKeyReturns400() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-400-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithSameIdempotencyKeyAndDifferentContractReturns422() {
        // Create two contracts
        String id1 = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-422A-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String id2 = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-422B-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Offer both contracts
        given().contentType("application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString()).post("/api/public/sales/contracts/" + id1 + "/offer").then().statusCode(200);
        given().contentType("application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString()).post("/api/public/sales/contracts/" + id2 + "/offer").then().statusCode(200);

        String idempotencyKey = java.util.UUID.randomUUID().toString();

        // Accept contract 1 — succeeds
        given()
            .contentType("application/json")
            .header("Idempotency-Key", idempotencyKey)
            .when()
            .post("/api/public/sales/contracts/" + id1 + "/accept")
            .then()
            .statusCode(200);

        // Accept contract 2 with the SAME key — 422 because fingerprint differs
        given()
            .contentType("application/json")
            .header("Idempotency-Key", idempotencyKey)
            .when()
            .post("/api/public/sales/contracts/" + id2 + "/accept")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithSameIdempotencyKeyTwiceReturnsSameCheckoutUrl() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-REPLAY-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString()).post("/api/public/sales/contracts/" + id + "/offer").then().statusCode(200);

        String idempotencyKey = java.util.UUID.randomUUID().toString();

        // First accept — creates payment
        String checkoutUrl1 = given()
            .contentType("application/json")
            .header("Idempotency-Key", idempotencyKey)
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200)
            .extract()
            .path("checkoutUrl");

        // Second accept with the same key — replays the cached result
        String checkoutUrl2 = given()
            .contentType("application/json")
            .header("Idempotency-Key", idempotencyKey)
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200)
            .extract()
            .path("checkoutUrl");

        assertEquals(checkoutUrl1, checkoutUrl2,
            "Retry with same Idempotency-Key must return the same checkout URL");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithTenConcurrentRequestsSameKeyAllReturnSameResult() throws Exception {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-RACE-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then().statusCode(200);

        String idempotencyKey = UUID.randomUUID().toString();
        int threads = 10;
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready =
            new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch go =
            new java.util.concurrent.CountDownLatch(1);

        List<java.util.concurrent.Future<int[]>> futures = new java.util.ArrayList<>();
        List<String> urls = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                io.restassured.response.Response r = given()
                    .contentType("application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .when()
                    .post("/api/public/sales/contracts/" + id + "/accept");
                int status = r.statusCode();
                String url = status == 200 ? r.jsonPath().getString("checkoutUrl") : null;
                if (url != null) urls.add(url);
                return new int[]{status};
            }));
        }

        // Release all threads simultaneously
        ready.await();
        go.countDown();

        List<Integer> statuses = new java.util.ArrayList<>();
        for (var f : futures) {
            statuses.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS)[0]);
        }
        pool.shutdown();

        // Every request must succeed and return the same checkout URL.
        assertEquals(threads, statuses.size(), "All threads must have completed");
        statuses.forEach(s ->
            assertEquals(200, s, "Every concurrent request must return 200, got: " + statuses));
        assertEquals(threads, urls.size());
        urls.forEach(u -> assertEquals(urls.get(0), u,
            "All responses must return the same checkout URL"));

        // Exactly one payment transaction and one idempotency record must exist.
        long txCount = em.createQuery(
                "SELECT COUNT(t) FROM PaymentTransaction t WHERE t.contractId = :cid",
                Long.class)
            .setParameter("cid", id)
            .getSingleResult();
        assertEquals(1, txCount, "Exactly one PaymentTransaction must exist after the race");

        long recordCount = em.createQuery(
                "SELECT COUNT(r) FROM dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.IdempotencyRecord r " +
                "WHERE r.key = :key AND r.scope = 'contract_accept'",
                Long.class)
            .setParameter("key", idempotencyKey)
            .getSingleResult();
        assertEquals(1, recordCount, "Exactly one IdempotencyRecord must exist after the race");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithTenConcurrentRequestsDifferentKeysCreatesOnePayment() throws Exception {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(buildRequest("REST-IDEM-RACE-DIFF-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then().statusCode(200);

        int threads = 10;
        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready =
            new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch go =
            new java.util.concurrent.CountDownLatch(1);

        List<java.util.concurrent.Future<int[]>> futures = new java.util.ArrayList<>();
        List<String> urls = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            String key = UUID.randomUUID().toString(); // different key per request
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                io.restassured.response.Response r = given()
                    .contentType("application/json")
                    .header("Idempotency-Key", key)
                    .when()
                    .post("/api/public/sales/contracts/" + id + "/accept");
                int status = r.statusCode();
                String url = status == 200 ? r.jsonPath().getString("checkoutUrl") : null;
                if (url != null) urls.add(url);
                return new int[]{status};
            }));
        }

        ready.await();
        go.countDown();

        List<Integer> statuses = new java.util.ArrayList<>();
        for (var f : futures) {
            statuses.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS)[0]);
        }
        pool.shutdown();

        // The winner returns 200. Racers that lose on UQ_payment_transaction_contract
        // recover the winner's checkout URL and also return 200. Late requests that
        // see the committed AWAITING_PAYMENT state get 422 — the honest state-machine
        // answer for a logically different request. No other status is acceptable.
        assertEquals(threads, statuses.size());
        statuses.forEach(s ->
            org.junit.jupiter.api.Assertions.assertTrue(s == 200 || s == 422,
                "Concurrent accept must return 200 or 422, got: " + statuses));
        long okCount = statuses.stream().filter(s -> s == 200).count();
        org.junit.jupiter.api.Assertions.assertTrue(okCount >= 1,
            "At least one request must succeed, got: " + statuses);
        urls.forEach(u -> assertEquals(urls.get(0), u,
            "All 200 responses must return the same checkout URL"));

        // Exactly one payment transaction must exist — no double-charge possible.
        long txCount = em.createQuery(
                "SELECT COUNT(t) FROM PaymentTransaction t WHERE t.contractId = :cid",
                Long.class)
            .setParameter("cid", id)
            .getSingleResult();
        assertEquals(1, txCount,
            "Exactly one PaymentTransaction must exist even under different-key races");
    }

    // ==================== Create idempotency tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createWithoutIdempotencyKeyReturns400() {
        given()
            .contentType("application/json")
            .body(buildRequest("REST-CREATE-IDEM-400-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createWithSameKeyTwiceReturnsSameContract() {
        String key = UUID.randomUUID().toString();
        String ref = "REST-CREATE-IDEM-REPLAY-" + System.currentTimeMillis();

        String id1 = given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .body(buildRequest(ref))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String id2 = given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .body(buildRequest(ref))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        assertEquals(id1, id2,
            "Retry with the same Idempotency-Key must return the same contract");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createWithSameKeyAndDifferentBodyReturns422() {
        String key = UUID.randomUUID().toString();

        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .body(buildRequest("REST-CREATE-IDEM-422A-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201);

        // Same key but a different payload → fingerprint mismatch → 422.
        CreateCustomerContractRequest different =
            buildRequest("REST-CREATE-IDEM-422B-" + System.currentTimeMillis());
        different.getLineItems().get(0)
            .setProductCode("REST-CONTRACT-PROD-002");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .body(different)
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(422);
    }

    // ==================== Offer idempotency tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerWithoutIdempotencyKeyReturns400() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-IDEM-400-" + System.currentTimeMillis()))
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
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerWithSameKeyTwiceReplaysResult() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-IDEM-REPLAY-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String key = java.util.UUID.randomUUID().toString();

        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        // Replay with the same key returns the cached 200 even though the
        // contract is already OFFERED (a fresh offer would fail with 422).
        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerWithSameKeyOnDifferentContractReturns422() {
        String id1 = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-IDEM-422A-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String id2 = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-OFFER-IDEM-422B-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        String key = java.util.UUID.randomUUID().toString();

        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id1 + "/offer")
            .then()
            .statusCode(200);

        // Same key on a different contract → fingerprint mismatch → 422.
        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id2 + "/offer")
            .then()
            .statusCode(422);
    }

    // ==================== Idempotency-Key validation tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createWithOverlongIdempotencyKeyReturns400() {
        given()
            .contentType("application/json")
            .header("Idempotency-Key", "k".repeat(256))
            .body(buildRequest("REST-KEY-LEN-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptWithOverlongIdempotencyKeyReturns400() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-KEY-LEN-ACC-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", "k".repeat(256))
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(400);
    }

    // ==================== Retry-payment tests ====================

    private String createAwaitingPaymentContract(String ref) {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest(ref))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then()
            .statusCode(200);

        return id;
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void retryPaymentWithLiveSessionReturnsSameCheckoutUrl() {
        String id = createAwaitingPaymentContract(
            "REST-RETRY-LIVE-" + System.currentTimeMillis());

        String checkoutUrl = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/retry-payment")
            .then()
            .statusCode(200)
            .extract()
            .path("checkoutUrl");

        assertEquals("https://checkout.stripe.com/c/cs_rest_test_123", checkoutUrl);

        // Still exactly one payment transaction — the live session was reused.
        long txCount = em.createQuery(
                "SELECT COUNT(t) FROM PaymentTransaction t WHERE t.contractId = :cid",
                Long.class)
            .setParameter("cid", id)
            .getSingleResult();
        assertEquals(1, txCount);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void retryPaymentOnDraftContractReturns422() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .body(buildRequest("REST-RETRY-DRAFT-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .when()
            .post("/api/public/sales/contracts/" + id + "/retry-payment")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void retryPaymentAfterExpiryCreatesNewSession() throws Exception {
        String id = createAwaitingPaymentContract(
            "REST-RETRY-EXPIRED-" + System.currentTimeMillis());

        // Simulate session expiry: mark the PENDING transaction EXPIRED directly.
        utx.begin();
        try {
            em.createQuery(
                    "UPDATE PaymentTransaction t SET t.status = :expired " +
                    "WHERE t.contractId = :cid AND t.status = :pending")
                .setParameter("expired",
                    dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus.EXPIRED)
                .setParameter("pending",
                    dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus.PENDING)
                .setParameter("cid", id)
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        String key = java.util.UUID.randomUUID().toString();
        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id + "/retry-payment")
            .then()
            .statusCode(200)
            .body("checkoutUrl", equalTo("https://checkout.stripe.com/c/cs_rest_test_123"));

        // The replay returns the same response without re-executing.
        given()
            .contentType("application/json")
            .header("Idempotency-Key", key)
            .when()
            .post("/api/public/sales/contracts/" + id + "/retry-payment")
            .then()
            .statusCode(200)
            .body("checkoutUrl", equalTo("https://checkout.stripe.com/c/cs_rest_test_123"));

        // Two transactions now exist: the expired attempt and the new PENDING one.
        long txCount = em.createQuery(
                "SELECT COUNT(t) FROM PaymentTransaction t WHERE t.contractId = :cid",
                Long.class)
            .setParameter("cid", id)
            .getSingleResult();
        assertEquals(2, txCount);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void retryPaymentWithoutIdempotencyKeyReturns400() {
        String id = createAwaitingPaymentContract(
            "REST-RETRY-NOKEY-" + System.currentTimeMillis());

        given()
            .contentType("application/json")
            .when()
            .post("/api/public/sales/contracts/" + id + "/retry-payment")
            .then()
            .statusCode(400);
    }

    // ==================== List with orgId filter (boundary) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldFilterContractsByOrgId() {
        String ref = "REST-LIST-FILTER-" + System.currentTimeMillis();
        given()
            .contentType("application/json")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
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

    // ==================== State change view tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListStateChangesForContract() {
        String id = given()
            .contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(buildRequest("REST-STATE-CHANGES-" + System.currentTimeMillis()))
            .when()
            .post("/api/public/sales/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given().contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post("/api/public/sales/contracts/" + id + "/offer")
            .then().statusCode(200);

        given().contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post("/api/public/sales/contracts/" + id + "/accept")
            .then().statusCode(200);

        List<Map<String, Object>> changes = given()
            .when()
            .get("/api/public/sales/contracts/" + id + "/state-changes")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .extract()
            .jsonPath()
            .getList("$");

        // Start + offer + accept + approve + awaiting_payment = 5 transitions.
        assertEquals(5, changes.size());
        assertEquals("DRAFT", changes.get(0).get("toState"));
        assertEquals("OFFERED", changes.get(1).get("toState"));
        assertEquals("ACCEPTED", changes.get(2).get("toState"));
        assertEquals("APPROVED", changes.get(3).get("toState"));
        assertEquals("AWAITING_PAYMENT", changes.get(4).get("toState"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenListingStateChangesForMissingContract() {
        given()
            .when()
            .get("/api/public/sales/contracts/" + UUID.randomUUID() + "/state-changes")
            .then()
            .statusCode(404);
    }

    // ==================== Payment attempt view tests ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListPaymentAttemptsForContract() {
        String id = createAwaitingPaymentContract(
            "REST-PAYMENT-ATTEMPTS-" + System.currentTimeMillis());

        List<Map<String, Object>> attempts = given()
            .when()
            .get("/api/public/sales/contracts/" + id + "/payment-attempts")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .extract()
            .jsonPath()
            .getList("$");

        assertEquals("PENDING", attempts.get(0).get("status"));
        assertNotNull(attempts.get(0).get("id"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldGetSinglePaymentAttemptForContract() {
        String id = createAwaitingPaymentContract(
            "REST-PAYMENT-ATTEMPT-GET-" + System.currentTimeMillis());

        String txId = given()
            .when()
            .get("/api/public/sales/contracts/" + id + "/payment-attempts")
            .then()
            .statusCode(200)
            .extract()
            .path("id[0]");

        given()
            .when()
            .get("/api/public/sales/contracts/" + id + "/payment-attempts/" + txId)
            .then()
            .statusCode(200)
            .body("id", equalTo(txId))
            .body("status", equalTo("PENDING"))
            .body("contractId", nullValue())
            .body("productDefinitionId", nullValue());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenPaymentAttemptDoesNotBelongToContract() {
        String id = createAwaitingPaymentContract(
            "REST-PAYMENT-ATTEMPT-NOTFOUND-" + System.currentTimeMillis());

        given()
            .when()
            .get("/api/public/sales/contracts/" + id + "/payment-attempts/" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenListingPaymentAttemptsForMissingContract() {
        given()
            .when()
            .get("/api/public/sales/contracts/" + UUID.randomUUID() + "/payment-attempts")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "other-customer", roles = {"abstratium-abstrapact_user"})
    void shouldForbidPaymentAttemptAccessForOtherCustomer() throws Exception {
        String contractId = UUID.randomUUID().toString();
        String txId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String ref = "REST-PAYMENT-SCOPING-" + System.currentTimeMillis();

        // Create the product definition, contract, account role and payment transaction
        // via native SQL so the test identity (other-customer) never needs to read
        // tenant-scoped entities belonging to the default org.
        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_product_definition " +
                    "(id, organisation_id, product_code, description, billing_model, payment_model, " +
                    " product_valid_from, cross_tenant_api_allowed) " +
                    "VALUES (:id, :orgId, :code, :desc, 'FIXED_PRICE', 'PREPAID', :validFrom, true)")
                .setParameter("id", productId)
                .setParameter("orgId", defaultOrgId)
                .setParameter("code", "REST-PAYMENT-SCOPING-PROD-" + System.currentTimeMillis())
                .setParameter("desc", "Payment scoping test product")
                .setParameter("validFrom", java.sql.Date.valueOf(LocalDate.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_contract " +
                    "(id, organisation_id, contract_reference, contract_date, currency, " +
                    " grand_total, payment_model, state, public_notes, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :ref, :date, 'EUR', 100, 'PREPAID', 'AWAITING_PAYMENT', 'notes', :now, :now)")
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

            em.createNativeQuery(
                    "INSERT INTO T_payment_transaction " +
                    "(id, organisation_id, contract_id, product_definition_id, psp_identifier, " +
                    " correlation_id, gross_amount, currency, status, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :contractId, :productId, 'stripe', :correlationId, " +
                    " 100, 'EUR', 'PENDING', :now, :now)")
                .setParameter("id", txId)
                .setParameter("orgId", defaultOrgId)
                .setParameter("contractId", contractId)
                .setParameter("productId", productId)
                .setParameter("correlationId", UUID.randomUUID().toString())
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();

            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        given()
            .when()
            .get("/api/public/sales/contracts/" + contractId + "/payment-attempts")
            .then()
            .statusCode(403);

        given()
            .when()
            .get("/api/public/sales/contracts/" + contractId + "/payment-attempts/" + txId)
            .then()
            .statusCode(403);

        given()
            .when()
            .get("/api/public/sales/contracts/" + contractId + "/state-changes")
            .then()
            .statusCode(403);
    }

    // ==================== Security tests for new read endpoints ====================

    @Test
    void shouldRequireAuthenticationForStateChangesEndpoint() {
        given()
            .when()
            .get("/api/public/sales/contracts/" + UUID.randomUUID() + "/state-changes")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    void shouldRequireAuthenticationForPaymentAttemptsEndpoint() {
        given()
            .when()
            .get("/api/public/sales/contracts/" + UUID.randomUUID() + "/payment-attempts")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    // ==================== Organisation scoping on service layer ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldAllowStateChangesWhenCallerOrgMatchesContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-STATE-ORG-OK-" + System.currentTimeMillis());

        List<dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.ContractStateChangeResponse> changes =
            contractService.listStateChanges(contractId, "other-customer", defaultOrgId);

        assertEquals(5, changes.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldRejectStateChangesWhenCallerOrgDoesNotMatchContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-STATE-ORG-FORBIDDEN-" + System.currentTimeMillis());

        jakarta.ws.rs.WebApplicationException ex = org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.WebApplicationException.class,
            () -> contractService.listStateChanges(contractId, "other-customer", OTHER_ORG_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldAllowPaymentAttemptsWhenCallerOrgMatchesContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-PAYMENTS-ORG-OK-" + System.currentTimeMillis());

        List<dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.PaymentAttemptResponse> attempts =
            contractService.listPaymentAttempts(contractId, "other-customer", defaultOrgId);

        assertEquals(1, attempts.size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldRejectPaymentAttemptsWhenCallerOrgDoesNotMatchContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-PAYMENTS-ORG-FORBIDDEN-" + System.currentTimeMillis());

        jakarta.ws.rs.WebApplicationException ex = org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.WebApplicationException.class,
            () -> contractService.listPaymentAttempts(contractId, "other-customer", OTHER_ORG_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldAllowSinglePaymentAttemptWhenCallerOrgMatchesContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-PAYMENT-GET-ORG-OK-" + System.currentTimeMillis());
        String txId = contractService.listPaymentAttempts(contractId, "testuser", null).get(0).getId();

        dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.PaymentAttemptResponse attempt =
            contractService.getPaymentAttempt(contractId, txId, "other-customer", defaultOrgId);

        assertEquals(txId, attempt.getId());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void serviceShouldRejectSinglePaymentAttemptWhenCallerOrgDoesNotMatchContractOrg() {
        String contractId = createAwaitingPaymentContract(
            "REST-SVC-PAYMENT-GET-ORG-FORBIDDEN-" + System.currentTimeMillis());
        String txId = contractService.listPaymentAttempts(contractId, "testuser", null).get(0).getId();

        jakarta.ws.rs.WebApplicationException ex = org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.WebApplicationException.class,
            () -> contractService.getPaymentAttempt(contractId, txId, "other-customer", OTHER_ORG_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }
}
