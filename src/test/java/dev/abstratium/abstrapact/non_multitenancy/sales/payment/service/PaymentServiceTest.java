package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractLineItem;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceState;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link PaymentService} webhook result handling logic: deduplication, staleness
 * check, state transitions, and webhook event recording.
 *
 * <p>These tests set up contracts + product definitions + payment transactions directly
 * via JPA, then call {@link PaymentService#handlePaymentResult} with synthetic
 * {@link PaymentEventResult} objects (no Stripe SDK involved).
 */
@QuarkusTest
class PaymentServiceTest {

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentTransactionService transactionService;

    @Inject
    WebhookEventService webhookEventService;

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    private String contractId;
    private String orgId;
    private String correlationId;
    private PaymentTransaction tx;

    @BeforeEach
    @Transactional
    void setUp() {
        orgId = "test-org-payment";
        contractId = UUID.randomUUID().toString();
        correlationId = UUID.randomUUID().toString();

        // Create a minimal product definition
        NonMultitenancyProductDefinition pd = new NonMultitenancyProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setOrganisationId(orgId);
        pd.setProductCode("PAY-TEST-" + UUID.randomUUID());
        pd.setBillingModel(NonMultitenancyProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(NonMultitenancyProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(java.time.LocalDate.now());
        pd.setStripeSecretKey("sk_test");
        pd.setStripeWebhookSecret("whsec_test");
        em.persist(pd);
        String productDefinitionId = pd.getId();

        // Create a product instance
        NonMultitenancyProductInstance pi = new NonMultitenancyProductInstance();
        pi.setId(UUID.randomUUID().toString());
        pi.setOrganisationId(orgId);
        pi.setProductDefinition(pd);
        em.persist(pi);

        // Create a contract
        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(orgId);
        contract.setContractReference("PAY-TEST-" + UUID.randomUUID());
        contract.setContractDate(java.time.LocalDate.now());
        contract.setGrandTotal(new BigDecimal("100.00"));
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(dev.abstratium.abstrapact.contracts.entity.ContractState.AWAITING_PAYMENT);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        // Create a line item linking contract to product instance
        NonMultitenancyContractLineItem li = new NonMultitenancyContractLineItem();
        li.setId(UUID.randomUUID().toString());
        li.setOrganisationId(orgId);
        li.setContract(contract);
        li.setProductInstance(pi);
        li.setLineTotal(new BigDecimal("100.00"));
        li.setDisplayOrder(0);
        em.persist(li);

        // Create a process instance (required by SalesProcessService.transitionToRunning)
        NonMultitenancyProcessInstance process = new NonMultitenancyProcessInstance();
        process.setId(UUID.randomUUID().toString());
        process.setOrganisationId(orgId);
        process.setContractId(contractId);
        process.setProcessName("sales");
        process.setProcessVersion("1");
        process.setState(ProcessInstanceState.IN_PROGRESS);
        em.persist(process);

        // Create a PENDING payment transaction
        tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(orgId);
        tx.setContractId(contractId);
        tx.setProductDefinitionId(productDefinitionId);
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(correlationId);
        tx.setPspSessionId("cs_test_123");
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

    // ==================== Success path ====================

    @Test
    @Transactional
    void handlePaymentResultSuccessTransitionsTransactionAndContract() {
        PaymentEventResult result = successResult(correlationId);

        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, updated.getStatus());
        assertEquals(0, new BigDecimal("0.59").compareTo(updated.getFeeAmount()));
        assertEquals(0, new BigDecimal("99.41").compareTo(updated.getNetAmount()));
        assertEquals("pi_test_123", updated.getPspTransactionRef());

        // Contract should have transitioned to RUNNING
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(dev.abstratium.abstrapact.contracts.entity.ContractState.RUNNING,
            contract.getState());

        // Webhook event should be recorded as PROCESSED
        assertEquals(1, webhookEventCount());
        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.PROCESSED, event.getProcessingResult());
        assertTrue(event.isMatched());
    }

    // ==================== Duplicate event ====================

    @Test
    @Transactional
    void handlePaymentResultDuplicateEventDoesNotChangeState() {
        PaymentEventResult result = successResult(correlationId);
        paymentService.handlePaymentResult(result);

        // Send the same event again
        PaymentEventResult duplicate = successResult(correlationId);
        duplicate.setPspEventId("evt_same"); // same event id as first
        // Actually, the first result has pspEventId "evt_succ", so let's use the same
        duplicate.setPspEventId(result.getPspEventId());
        paymentService.handlePaymentResult(duplicate);

        // Transaction should still be SUCCEEDED (not changed by duplicate)
        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, updated.getStatus());

        // Only one webhook event row (deduplication)
        assertEquals(1, webhookEventCount());
    }

    // ==================== Stale transaction ====================

    @Test
    @Transactional
    void handlePaymentResultStaleTransactionMarksStaleAndDoesNotTransition() {
        // Make the transaction old enough to be stale
        tx.setCreatedAt(LocalDateTime.now().minusHours(25));
        em.merge(tx);

        PaymentEventResult result = successResult(correlationId);
        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.STALE, updated.getStatus());

        // Contract should NOT have transitioned
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(dev.abstratium.abstrapact.contracts.entity.ContractState.AWAITING_PAYMENT,
            contract.getState());

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.STALE, event.getProcessingResult());
    }

    // ==================== Unmatched event (no correlation id) ====================

    @Test
    @Transactional
    void handlePaymentResultNoCorrelationIdRecordsUnmatched() {
        PaymentEventResult result = successResult(null);
        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, updated.getStatus());

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.UNMATCHED, event.getProcessingResult());
        assertFalse(event.isMatched());
    }

    // ==================== Unmatched event (unknown correlation id) ====================

    @Test
    @Transactional
    void handlePaymentResultUnknownCorrelationIdRecordsUnmatched() {
        PaymentEventResult result = successResult("unknown-corr-id");
        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, updated.getStatus());

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.UNMATCHED, event.getProcessingResult());
    }

    // ==================== Failure path ====================

    @Test
    @Transactional
    void handlePaymentResultFailureMarksFailedAndDoesNotTransition() {
        PaymentEventResult result = new PaymentEventResult();
        result.setPspEventId("evt_fail");
        result.setEventType("checkout.session.async_payment_failed");
        result.setCorrelationId(correlationId);
        result.setMatched(true);
        result.setStatus(PaymentTransaction.PaymentStatus.FAILED);
        result.setRawPayload("{}");

        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.FAILED, updated.getStatus());

        // Contract stays AWAITING_PAYMENT
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        assertEquals(dev.abstratium.abstrapact.contracts.entity.ContractState.AWAITING_PAYMENT,
            contract.getState());

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.PROCESSED, event.getProcessingResult());
    }

    // ==================== Terminal state → duplicate ====================

    @Test
    @Transactional
    void handlePaymentResultOnSucceededTransactionRecordsDuplicate() {
        // Mark the transaction as already SUCCEEDED
        tx.setStatus(PaymentTransaction.PaymentStatus.SUCCEEDED);
        em.merge(tx);

        PaymentEventResult result = successResult(correlationId);
        paymentService.handlePaymentResult(result);

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.DUPLICATE, event.getProcessingResult());
    }

    // ==================== Ignored event type ====================

    @Test
    @Transactional
    void handlePaymentResultIgnoredEventTypeRecordsIgnored() {
        PaymentEventResult result = new PaymentEventResult();
        result.setPspEventId("evt_ignored");
        result.setEventType("charge.updated");
        result.setCorrelationId(correlationId);
        result.setMatched(true);
        result.setStatus(PaymentTransaction.PaymentStatus.PENDING);
        result.setRawPayload("{}");

        paymentService.handlePaymentResult(result);

        PaymentTransaction updated = transactionService.findById(tx.getId()).orElseThrow();
        assertEquals(PaymentTransaction.PaymentStatus.PENDING, updated.getStatus());

        WebhookEvent event = findWebhookEvent();
        assertEquals(WebhookEvent.ProcessingResult.IGNORED, event.getProcessingResult());
    }

    // ==================== helpers ====================

    private PaymentEventResult successResult(String corrId) {
        PaymentEventResult result = new PaymentEventResult();
        result.setPspEventId("evt_succ_" + UUID.randomUUID());
        result.setEventType("checkout.session.completed");
        result.setCorrelationId(corrId);
        result.setMatched(corrId != null);
        result.setPspSessionId("cs_test_123");
        result.setPspTransactionRef("pi_test_123");
        result.setGrossAmount(new BigDecimal("100.00"));
        result.setFeeAmount(new BigDecimal("0.59"));
        result.setCurrency("EUR");
        result.setStatus(PaymentTransaction.PaymentStatus.SUCCEEDED);
        result.setRawPayload("{\"id\":\"evt_succ\"}");
        return result;
    }

    private long webhookEventCount() {
        return em.createQuery("SELECT COUNT(w) FROM WebhookEvent w", Long.class)
            .getSingleResult();
    }

    private WebhookEvent findWebhookEvent() {
        return em.createQuery("SELECT w FROM WebhookEvent w", WebhookEvent.class)
            .setMaxResults(1)
            .getSingleResult();
    }
}
