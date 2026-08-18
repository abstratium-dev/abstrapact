package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractLineItem;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.test.TestDataCleaner;
import dev.abstratium.test.payment.WebhookSignatureTestHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link StripePSPService} against a Wiremock server standing in for the Stripe API.
 *
 * <p>Payment creation stubs the Stripe Checkout Session creation endpoint. Webhook
 * processing uses {@link WebhookSignatureTestHelper} to generate valid signatures, so the
 * full {@code Webhook.constructEvent} verification path is exercised.
 */
@QuarkusTest
@TestProfile(StripePSPServiceTest.TestProfile.class)
class StripePSPServiceTest {

    /** Wiremock port — must match abstrapact.payment.stripe.api-base in the test profile. */
    static final int WIREMOCK_PORT = 19998;

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

    static WireMockServer wireMock;

    @Inject
    StripePSPService psp;

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    @Inject
    ObjectMapper objectMapper;

    static final String SECRET_KEY = "sk_test_123";
    static final String WEBHOOK_SECRET = "whsec_test_456";

    static String productDefId;

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
    @Transactional
    void setUp() {
        wireMock.resetAll();
        // Create the full chain: product definition → product instance → contract → line item
        // → payment transaction. This allows the webhook secret to be resolved by either
        // correlation id or session id (no brute-force across all products).
        setupProductAndTransaction("corr-default", "cs_default");
    }

    /**
     * Creates the full DB chain for webhook signature resolution:
     * ProductDefinition (with Stripe secrets) → ProductInstance → Contract → LineItem
     * + PaymentTransaction (with the given correlation id and session id).
     */
    @Transactional
    void setupProductAndTransaction(String correlationId, String sessionId) {
        String orgId = "test-org-stripe";

        NonMultitenancyProductDefinition pd = new NonMultitenancyProductDefinition();
        productDefId = UUID.randomUUID().toString();
        pd.setId(productDefId);
        pd.setOrganisationId(orgId);
        pd.setProductCode("STRIPE-TEST-PROD-" + UUID.randomUUID());
        pd.setBillingModel(NonMultitenancyProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(NonMultitenancyProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        pd.setStripeSecretKey(SECRET_KEY);
        pd.setStripeWebhookSecret(WEBHOOK_SECRET);
        em.persist(pd);

        NonMultitenancyProductInstance pi = new NonMultitenancyProductInstance();
        pi.setId(UUID.randomUUID().toString());
        pi.setOrganisationId(orgId);
        pi.setProductDefinition(pd);
        em.persist(pi);

        String contractId = UUID.randomUUID().toString();
        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(orgId);
        contract.setContractReference("STRIPE-TEST-" + UUID.randomUUID());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setGrandTotal(new BigDecimal("12.34"));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        NonMultitenancyContractLineItem li = new NonMultitenancyContractLineItem();
        li.setId(UUID.randomUUID().toString());
        li.setOrganisationId(orgId);
        li.setContract(contract);
        li.setProductInstance(pi);
        li.setLineTotal(new BigDecimal("12.34"));
        li.setDisplayOrder(0);
        em.persist(li);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(orgId);
        tx.setContractId(contractId);
        tx.setProductDefinitionId(pd.getId());
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(correlationId);
        tx.setPspSessionId(sessionId);
        tx.setGrossAmount(new BigDecimal("12.34"));
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

    // ==================== Payment creation ====================

    @Test
    void createPaymentReturnsCheckoutUrlAndSessionId() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/checkout/sessions"))
            .willReturn(okJson("""
                {
                  "id": "cs_test_123",
                  "object": "checkout.session",
                  "url": "https://checkout.stripe.com/c/cs_test_123",
                  "payment_status": "unpaid",
                  "status": "open"
                }
                """)));

        CreatePaymentRequest req = newPaymentRequest();
        CreatePaymentResponse resp = psp.createPayment(req);

        assertEquals("https://checkout.stripe.com/c/cs_test_123", resp.getCheckoutUrl());
        assertEquals("cs_test_123", resp.getPspSessionId());
    }

    @Test
    void createPaymentThrowsOnStripeError() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/checkout/sessions"))
            .willReturn(aResponse().withStatus(400)
                .withBody("""
                    {"error": {"type": "invalid_request_error", "message": "Invalid amount"}}
                    """)));

        CreatePaymentRequest req = newPaymentRequest();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> psp.createPayment(req));
        assertEquals(400, ex.getResponse().getStatus());
    }

    // ==================== Webhook: checkout.session.completed (paid) ====================

    @Test
    void processWebhookEventCheckoutCompletedPaidMapsToSucceeded() {
        String correlationId = "corr-123";
        String sessionId = "cs_test_1";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = checkoutSessionCompletedPayload(correlationId, "paid", sessionId, "pi_test_1", 1234, "eur");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertEquals("checkout.session.completed", result.getEventType());
        assertEquals(correlationId, result.getCorrelationId());
        assertTrue(result.isMatched());
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, result.getStatus());
        assertEquals(sessionId, result.getPspSessionId());
        assertEquals("pi_test_1", result.getPspTransactionRef());
        assertEquals("eur", result.getCurrency());
        assertEquals(new BigDecimal("12.34"), result.getGrossAmount());
    }

    @Test
    void processWebhookEventCheckoutCompletedUnpaidMapsToPending() {
        String correlationId = "corr-456";
        String sessionId = "cs_test_2";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = checkoutSessionCompletedPayload(correlationId, "unpaid", sessionId, "pi_test_2", 5000, "usd");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertEquals(PaymentTransaction.PaymentStatus.PENDING, result.getStatus());
        assertEquals(correlationId, result.getCorrelationId());
    }

    // ==================== Webhook: checkout.session.async_payment_failed ====================

    @Test
    void processWebhookEventAsyncPaymentFailedMapsToFailed() {
        String correlationId = "corr-fail";
        String sessionId = "cs_fail";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = asyncPaymentFailedPayload(correlationId, sessionId, "pi_fail", 2000, "eur");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertEquals(PaymentTransaction.PaymentStatus.FAILED, result.getStatus());
        assertEquals(correlationId, result.getCorrelationId());
    }

    // ==================== Webhook: payment_intent.succeeded ====================

    @Test
    void processWebhookEventPaymentIntentSucceededMapsToSucceeded() {
        String correlationId = "corr-pi";
        String sessionId = "cs_pi_succ";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = paymentIntentSucceededPayload(correlationId, "pi_succ", 9999, "chf");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, result.getStatus());
        assertEquals("pi_succ", result.getPspTransactionRef());
        assertEquals(new BigDecimal("99.99"), result.getGrossAmount());
        assertEquals("chf", result.getCurrency());
    }

    // ==================== Webhook: charge.updated (fee update) ====================

    @Test
    void processWebhookEventChargeUpdatedMapsToPendingAndExtractsFee() {
        String correlationId = "corr-fee";
        String sessionId = "cs_fee";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = chargeUpdatedPayload(correlationId, "pi_fee", "txn_fee", 10000, 59, "eur");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        // charge.updated is a fee-only update — status stays PENDING (PaymentService records IGNORED).
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("0.59"), result.getFeeAmount());
        assertEquals(new BigDecimal("100.00"), result.getGrossAmount());
    }

    // ==================== Webhook: unknown event type ====================

    @Test
    void processWebhookEventUnknownTypeMapsToPending() {
        String correlationId = "corr-unknown";
        String sessionId = "cs_unknown";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = """
            {
              "id": "evt_unknown",
              "type": "invoice.paid",
              "data": {"object": {"metadata": {"correlation_id": "%s"}, "id": "%s"}}
            }
            """.formatted(correlationId, sessionId);
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertEquals("invoice.paid", result.getEventType());
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, result.getStatus());
        assertEquals(correlationId, result.getCorrelationId());
    }

    // ==================== Webhook: no correlation id (resolved by session id) ====================

    @Test
    void processWebhookEventNoCorrelationIdIsUnmatchedButVerifiedBySessionId() {
        // The session id "cs_default" was set up in setUp() with a matching transaction.
        // Even without a correlation id in the metadata, the webhook secret can be resolved
        // via the session id → PaymentTransaction → product definition.
        String payload = """
            {
              "id": "evt_no_corr",
              "type": "checkout.session.completed",
              "data": {"object": {"metadata": {}, "payment_status": "paid",
                "id": "cs_default", "payment_intent": "pi_x", "amount_total": 100, "currency": "eur"}}
            }
            """;
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        PaymentEventResult result = psp.processWebhookEvent(payload, signature);

        assertNull(result.getCorrelationId());
        assertFalse(result.isMatched());
    }

    // ==================== Webhook: invalid signature ====================

    @Test
    void processWebhookEventInvalidSignatureThrows400() {
        String correlationId = "corr-bad";
        String sessionId = "cs_bad_sig";
        setupProductAndTransaction(correlationId, sessionId);
        String payload = checkoutSessionCompletedPayload(correlationId, "paid", sessionId, "pi_bad", 100, "eur");
        String badSignature = WebhookSignatureTestHelper.malformedSignature(payload, System.currentTimeMillis() / 1000);

        // The secret is resolved by correlation id, but the signature doesn't match → 400.
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> psp.processWebhookEvent(payload, badSignature));
        assertEquals(400, ex.getResponse().getStatus());
    }

    // ==================== Webhook: no matching product (unresolvable) ====================

    @Test
    void processWebhookEventNoMatchingProductThrows400() {
        // No transaction exists for this correlation id or session id → no secret to verify
        // with → reject the webhook.
        String payload = checkoutSessionCompletedPayload("corr-nonexistent", "paid", "cs_nonexistent", "pi_x", 100, "eur");
        String signature = WebhookSignatureTestHelper.sign(payload, WEBHOOK_SECRET);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> psp.processWebhookEvent(payload, signature));
        assertEquals(400, ex.getResponse().getStatus());
    }

    // ==================== helpers ====================

    private CreatePaymentRequest newPaymentRequest() {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setContractId("contract-1");
        req.setCorrelationId("corr-new");
        req.setAmount(new BigDecimal("12.34"));
        req.setCurrency("EUR");
        req.setDescription("Test contract");
        req.setSuccessUrl("http://localhost:10088/public/payment/success?session_id={CHECKOUT_SESSION_ID}");
        req.setCancelUrl("http://localhost:10088/public/payment/cancel?session_id={CHECKOUT_SESSION_ID}");
        req.setStripeSecretKey(SECRET_KEY);
        return req;
    }

    private static String checkoutSessionCompletedPayload(String correlationId, String paymentStatus,
            String sessionId, String paymentIntent, long amountTotal, String currency) {
        return """
            {
              "id": "evt_%s",
              "type": "checkout.session.completed",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_intent": "%s",
                  "payment_status": "%s",
                  "amount_total": %d,
                  "currency": "%s",
                  "metadata": {"correlation_id": "%s"}
                }
              }
            }
            """.formatted(sessionId, sessionId, paymentIntent, paymentStatus, amountTotal, currency, correlationId);
    }

    private static String asyncPaymentFailedPayload(String correlationId, String sessionId,
            String paymentIntent, long amountTotal, String currency) {
        return """
            {
              "id": "evt_fail_%s",
              "type": "checkout.session.async_payment_failed",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_intent": "%s",
                  "payment_status": "unpaid",
                  "amount_total": %d,
                  "currency": "%s",
                  "metadata": {"correlation_id": "%s"}
                }
              }
            }
            """.formatted(sessionId, sessionId, paymentIntent, amountTotal, currency, correlationId);
    }

    private static String paymentIntentSucceededPayload(String correlationId, String paymentIntent,
            long amount, String currency) {
        return """
            {
              "id": "evt_pi_%s",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "%s",
                  "object": "payment_intent",
                  "amount": %d,
                  "currency": "%s",
                  "status": "succeeded",
                  "metadata": {"correlation_id": "%s"}
                }
              }
            }
            """.formatted(paymentIntent, paymentIntent, amount, currency, correlationId);
    }

    private static String chargeUpdatedPayload(String correlationId, String paymentIntent,
            String balanceTxnId, long amount, long fee, String currency) {
        // Stripe sends balance_transaction as an expanded object when the charge.updated
        // event is delivered, so getBalanceTransactionObject() returns a populated object.
        return """
            {
              "id": "evt_charge_%s",
              "type": "charge.updated",
              "data": {
                "object": {
                  "id": "ch_%s",
                  "object": "charge",
                  "payment_intent": "%s",
                  "amount": %d,
                  "currency": "%s",
                  "balance_transaction": {
                    "id": "%s",
                    "object": "balance_transaction",
                    "amount": %d,
                    "fee": %d,
                    "currency": "%s"
                  },
                  "metadata": {"correlation_id": "%s"}
                }
              }
            }
            """.formatted(balanceTxnId, paymentIntent, paymentIntent, amount, currency,
                balanceTxnId, amount, fee, currency, correlationId);
    }
}
