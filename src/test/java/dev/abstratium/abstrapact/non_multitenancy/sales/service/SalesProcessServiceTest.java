package dev.abstratium.abstrapact.non_multitenancy.sales.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentTransactionService;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerContractResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.service.ProductDefinitionService;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link SalesProcessService} contract lifecycle transitions, including the payment
 * handling step that transitions prepaid contracts to {@code AWAITING_PAYMENT} and creates
 * a Stripe Checkout Session (via Wiremock).
 */
@QuarkusTest
@TestProfile(SalesProcessServiceTest.TestProfile.class)
class SalesProcessServiceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "abstrapact.payment.stripe.api-base",
                "http://localhost:" + WIREMOCK_PORT,
                "abstrapact.payment.stripe.success-url",
                "http://localhost:10088/public/payment/success?session_id={CHECKOUT_SESSION_ID}",
                "abstrapact.payment.stripe.cancel-url",
                "http://localhost:10088/public/payment/cancel?session_id={CHECKOUT_SESSION_ID}",
                "abstrapact.payment.psp", "stripe"
            );
        }
    }

    static final int WIREMOCK_PORT = 19997;

    static WireMockServer wireMock;

    @Inject
    SalesProcessService salesProcessService;

    @Inject
    NonMultitenancyCustomerContractService contractService;

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    PaymentTransactionService transactionService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction tx;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private static final String ACCOUNT_ID = "sales-process-test-user";
    private static final String STRIPE_SECRET_KEY = "sk_test_sales";
    private static final String STRIPE_WEBHOOK_SECRET = "whsec_test_sales";

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
    void setUp() {
        wireMock.resetAll();

        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("SP-PROD-001");
        pd.setDescription("Sales Process Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        pd.setStripeSecretKey(STRIPE_SECRET_KEY);
        pd.setStripeWebhookSecret(STRIPE_WEBHOOK_SECRET);
        productDefinitionService.createProductDefinition(pd);

        // Stub the Stripe Checkout Session creation endpoint
        wireMock.stubFor(post(urlPathEqualTo("/v1/checkout/sessions"))
            .willReturn(okJson("""
                {
                  "id": "cs_test_sales_123",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/cs_test_sales_123",
                  "payment_status": "unpaid",
                  "status": "open"
                }
                """)));
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private CustomerContractResponse createDraftContract() {
        currentOrgContext.setOrgId(defaultOrgId);
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode(OrgScopedCodec.encode(defaultOrgId, "SP-PROD-001", "Product"));
        li.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference("SP-TEST-" + UUID.randomUUID());
        req.setLineItems(List.of(li));
        return contractService.createContract(req, defaultOrgId, ACCOUNT_ID);
    }

    private NonMultitenancyProcessInstance loadProcess(String contractId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyProcessInstance p WHERE p.contractId = :contractId",
                NonMultitenancyProcessInstance.class)
            .setParameter("contractId", contractId)
            .getSingleResult();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void startSalesProcessCreatesProcessInstanceInProgress() {
        CustomerContractResponse response = createDraftContract();

        NonMultitenancyProcessInstance process = loadProcess(response.getId());

        assertEquals(ProcessInstanceState.IN_PROGRESS, process.getState());
        assertEquals(defaultOrgId, process.getOrganisationId());
        assertEquals("sales-process", process.getProcessName());
        assertEquals(response.getId(), process.getContractId());
        assertEquals("1.0", process.getProcessVersion());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void startSalesProcessRecordsInitialStep() {
        CustomerContractResponse response = createDraftContract();

        NonMultitenancyProcessInstance process = loadProcess(response.getId());

        assertFalse(process.getSteps().isEmpty());
        assertEquals(ContractState.DRAFT.name(), process.getSteps().get(0).getToState());
        assertEquals(ACCOUNT_ID, process.getSteps().get(0).getActorUserId());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractTransitionsDraftToOffered() {
        CustomerContractResponse response = createDraftContract();

        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.OFFERED, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractRecordsStep() {
        CustomerContractResponse response = createDraftContract();

        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long offerSteps = process.getSteps().stream()
            .filter(s -> ContractState.OFFERED.name().equals(s.getToState()))
            .count();
        assertEquals(1, offerSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsWhenNotDraft() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ==================== Accept + auto-approve + payment (prepaid) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractTransitionsOfferedToAwaitingPaymentAndReturnsCheckoutUrl() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        String checkoutUrl = salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        // Prepaid: contract transitions to AWAITING_PAYMENT (not APPROVED, since
        // auto-approval + payment handling moves it forward in one call).
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
        assertNotNull(checkoutUrl);
        assertTrue(checkoutUrl.contains("checkout.stripe.com"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractRecordsAcceptanceApprovalAndAwaitingPaymentSteps() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long acceptSteps = process.getSteps().stream()
            .filter(s -> ContractState.ACCEPTED.name().equals(s.getToState()))
            .count();
        assertEquals(1, acceptSteps);
        long approvedSteps = process.getSteps().stream()
            .filter(s -> ContractState.APPROVED.name().equals(s.getToState()))
            .count();
        assertEquals(1, approvedSteps);
        long awaitingSteps = process.getSteps().stream()
            .filter(s -> ContractState.AWAITING_PAYMENT.name().equals(s.getToState()))
            .count();
        assertEquals(1, awaitingSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractCreatesPendingPaymentTransaction() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        // A PENDING payment transaction should exist for this contract
        var transactions = em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.contractId = :cid",
                PaymentTransaction.class)
            .setParameter("cid", response.getId())
            .getResultList();
        assertEquals(1, transactions.size());
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, transactions.get(0).getStatus());
        assertEquals("cs_test_sales_123", transactions.get(0).getPspSessionId());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsWhenNotOffered() {
        CustomerContractResponse response = createDraftContract();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ==================== Approval (prepaid, via triggerPaymentHandling) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractTransitionsAcceptedToAwaitingPayment() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        // Move to ACCEPTED without auto-approval by manually setting the state.
        setContractState(response.getId(), ContractState.ACCEPTED);

        String checkoutUrl = salesProcessService.approveContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
        assertNotNull(checkoutUrl);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractRecordsStep() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.ACCEPTED);

        salesProcessService.approveContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long approvedSteps = process.getSteps().stream()
            .filter(s -> ContractState.APPROVED.name().equals(s.getToState()))
            .count();
        assertEquals(1, approvedSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsWhenNotAccepted() {
        CustomerContractResponse response = createDraftContract();
        // Contract is still DRAFT.
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ==================== Payment handling (prepaid) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingTransitionsApprovedToAwaitingPayment() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.APPROVED);

        String checkoutUrl = salesProcessService.triggerPaymentHandling(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
        assertNotNull(checkoutUrl);
        assertTrue(checkoutUrl.contains("checkout.stripe.com"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingFailsWhenNotApproved() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        // Contract is OFFERED, not APPROVED.

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.triggerPaymentHandling(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ==================== transitionToRunning ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void transitionToRunningTransitionsAwaitingPaymentToRunning() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);
        // Contract is now AWAITING_PAYMENT

        salesProcessService.transitionToRunning(response.getId(), "system");

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.RUNNING, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void transitionToRunningFailsWhenNotAwaitingPayment() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        // Contract is OFFERED.

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.transitionToRunning(response.getId(), "system"));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void transitionToRunningFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.transitionToRunning("does-not-exist", "system"));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ==================== Helper ====================

    /**
     * Directly sets the contract state via JPA, bypassing the sales process.
     * Used to set up intermediate states (ACCEPTED, APPROVED) for testing
     * individual transition methods in isolation.
     */
    private void setContractState(String contractId, ContractState state) {
        try {
            tx.begin();
            NonMultitenancyContract c = em.find(NonMultitenancyContract.class, contractId);
            c.setState(state);
            em.merge(c);
            tx.commit();
        } catch (Exception e) {
            try { tx.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ==================== Account-link authorisation ====================

    private static final String OTHER_ACCOUNT_ID = "intruder-account";

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.APPROVED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.triggerPaymentHandling(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    // ==================== Postpaid (unsupported) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingThrows422ForPostpaidContract() {
        // Create a POSTPAID product and contract
        ProductDefinition postpaid = new ProductDefinition();
        postpaid.setId(UUID.randomUUID().toString());
        postpaid.setProductCode("SP-PROD-POSTPAID");
        postpaid.setDescription("Postpaid test product");
        postpaid.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        postpaid.setPaymentModel(ProductDefinition.PaymentModel.POSTPAID);
        postpaid.setProductValidFrom(LocalDate.now());
        postpaid.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(postpaid);

        currentOrgContext.setOrgId(defaultOrgId);
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode(OrgScopedCodec.encode(defaultOrgId, "SP-PROD-POSTPAID", "Product"));
        li.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference("SP-POSTPAID-" + UUID.randomUUID());
        req.setLineItems(List.of(li));
        CustomerContractResponse response = contractService.createContract(req, defaultOrgId, ACCOUNT_ID);

        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.APPROVED);

        // Postpaid payment model is not yet supported — should throw 422
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.triggerPaymentHandling(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());

        // Contract should remain APPROVED (not transitioned)
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.APPROVED, contract.getState());
    }
}
