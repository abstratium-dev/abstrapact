package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractLineItem;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceState;
import dev.abstratium.test.TestDataCleaner;
import dev.abstratium.test.payment.WebhookSignatureTestHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link PaymentWebhookResource}.
 *
 * <p>Posts real webhook payloads (with valid Stripe signatures) to the unauthenticated
 * webhook endpoint and verifies the full flow: signature verification → event recording →
 * transaction update → contract state transition.
 */
@QuarkusTest
class PaymentWebhookResourceTest {

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    private static final String WEBHOOK_SECRET = "whsec_integration_test";
    private static final String STRIPE_SECRET_KEY = "sk_test_integration";
    private static final String ORG_ID = "webhook-test-org";

    private String contractId;
    private String correlationId;
    private String pspSessionId;
    private String productDefinitionId;

    @BeforeEach
    @Transactional
    void setUp() {
        contractId = UUID.randomUUID().toString();
        correlationId = UUID.randomUUID().toString();
        pspSessionId = "cs_integration_" + UUID.randomUUID();

        // Product definition with Stripe credentials
        NonMultitenancyProductDefinition pd = new NonMultitenancyProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setOrganisationId(ORG_ID);
        pd.setProductCode("WEBHOOK-TEST-" + UUID.randomUUID());
        pd.setBillingModel(NonMultitenancyProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(NonMultitenancyProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        pd.setStripeSecretKey(STRIPE_SECRET_KEY);
        pd.setStripeWebhookSecret(WEBHOOK_SECRET);
        em.persist(pd);
        productDefinitionId = pd.getId();

        // Product instance
        NonMultitenancyProductInstance pi = new NonMultitenancyProductInstance();
        pi.setId(UUID.randomUUID().toString());
        pi.setOrganisationId(ORG_ID);
        pi.setProductDefinition(pd);
        em.persist(pi);

        // Contract in AWAITING_PAYMENT
        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(ORG_ID);
        contract.setContractReference("WEBHOOK-TEST-" + UUID.randomUUID());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setGrandTotal(new BigDecimal("100.00"));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        // Line item
        NonMultitenancyContractLineItem li = new NonMultitenancyContractLineItem();
        li.setId(UUID.randomUUID().toString());
        li.setOrganisationId(ORG_ID);
        li.setContract(contract);
        li.setProductInstance(pi);
        li.setLineTotal(new BigDecimal("100.00"));
        li.setDisplayOrder(0);
        em.persist(li);

        // Process instance (needed by transitionToRunning)
        NonMultitenancyProcessInstance process = new NonMultitenancyProcessInstance();
        process.setId(UUID.randomUUID().toString());
        process.setOrganisationId(ORG_ID);
        process.setContractId(contractId);
        process.setProcessName("sales");
        process.setProcessVersion("1");
        process.setState(ProcessInstanceState.IN_PROGRESS);
        em.persist(process);

        // PENDING payment transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(ORG_ID);
        tx.setContractId(contractId);
        tx.setProductDefinitionId(productDefinitionId);
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(correlationId);
        tx.setPspSessionId(pspSessionId);
        tx.setGrossAmount(new BigDecimal("100.00"));
        tx.setCurrency("EUR");
        tx.setStatus(PaymentTransaction.PaymentStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        em.persist(tx);
        em.flush();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookSuccessEventTransitionsContractToRunning() {
        String payload = checkoutSessionCompletedPayload(correlationId, pspSessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);

        // Contract should have transitioned to RUNNING
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.RUNNING, contract.getState());

        // Transaction should be SUCCEEDED
        PaymentTransaction tx = findTransactionByCorrelationId(correlationId);
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, tx.getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookInvalidSignatureReturns400() {
        String payload = checkoutSessionCompletedPayload(correlationId, pspSessionId);
        String badSignature = WebhookSignatureTestHelper.malformedSignature(payload,
            System.currentTimeMillis() / 1000);

        given()
            .header("Stripe-Signature", badSignature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(400);

        // Contract should remain AWAITING_PAYMENT
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookDuplicateEventDoesNotChangeState() {
        String payload = checkoutSessionCompletedPayload(correlationId, pspSessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        // First event — should transition to RUNNING
        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);

        // Second event (same event id) — should be a duplicate, no state change
        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);

        // Contract should still be RUNNING (not changed by duplicate)
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.RUNNING, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookUnmatchedEventReturns400WhenProductCannotBeIdentified() {
        // A webhook with an unknown correlation id AND unknown session id cannot be
        // associated with any product → no webhook secret to verify against → 400.
        String payload = checkoutSessionCompletedPayload("unknown-corr-id", "cs_unknown");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(400);

        // Contract should remain AWAITING_PAYMENT
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookUnmatchedCorrelationButKnownSessionIdReturns200AndDoesNotTransition() {
        // A webhook with no correlation id in metadata but a known session id can still
        // be verified (secret resolved by session id). The event is UNMATCHED (no
        // correlation id) but the signature is valid → 200, no state change.
        String payload = checkoutSessionCompletedPayload(null, pspSessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);

        // Contract should remain AWAITING_PAYMENT (no correlation id → UNMATCHED)
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void webhookFailureEventMarksTransactionFailed() {
        String payload = asyncPaymentFailedPayload(correlationId, pspSessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);

        PaymentTransaction tx = findTransactionByCorrelationId(correlationId);
        assertEquals(PaymentTransaction.PaymentStatus.FAILED, tx.getStatus());

        // Contract stays AWAITING_PAYMENT
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(ContractState.AWAITING_PAYMENT, contract.getState());
    }

    @Test
    void webhookIsPublicAndDoesNotRequireAuthentication() {
        // No @TestSecurity annotation → no authenticated user.
        // The webhook endpoint is @PermitAll, so it should be accessible without auth.
        // Uses the known session id so the webhook secret can be resolved.
        String payload = checkoutSessionCompletedPayload(null, pspSessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        given()
            .header("Stripe-Signature", signature)
            .contentType("application/json")
            .body(payload)
        .when()
            .post("/public/payment/webhook")
        .then()
            .statusCode(200);
    }

    // ==================== helpers ====================

    private PaymentTransaction findTransactionByCorrelationId(String corrId) {
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.correlationId = :cid",
                PaymentTransaction.class)
            .setParameter("cid", corrId)
            .getSingleResult();
    }

    private static String checkoutSessionCompletedPayload(String correlationId, String sessionId) {
        String metadata = correlationId != null
            ? "\"metadata\": {\"correlation_id\": \"%s\"}".formatted(correlationId)
            : "\"metadata\": {}";
        return """
            {
              "id": "evt_integration_%s",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_intent": "pi_integration_%s",
                  "payment_status": "paid",
                  "amount_total": 10000,
                  "currency": "eur",
                  %s
                }
              }
            }
            """.formatted(sessionId, sessionId, sessionId, metadata);
    }

    private static String asyncPaymentFailedPayload(String correlationId, String sessionId) {
        return """
            {
              "id": "evt_fail_%s",
              "type": "checkout.session.async_payment_failed",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_intent": "pi_fail_%s",
                  "payment_status": "unpaid",
                  "amount_total": 10000,
                  "currency": "eur",
                  "metadata": {"correlation_id": "%s"}
                }
              }
            }
            """.formatted(sessionId, sessionId, sessionId, correlationId);
    }
}
