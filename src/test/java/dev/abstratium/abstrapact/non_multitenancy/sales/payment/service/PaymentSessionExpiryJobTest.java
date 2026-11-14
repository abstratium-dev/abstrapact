package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link PaymentSessionExpiryJob}: {@code PENDING} transactions older than
 * the session TTL are marked {@code EXPIRED} so the customer can retry payment;
 * fresh {@code PENDING} transactions are left alone.
 */
@QuarkusTest
class PaymentSessionExpiryJobTest {

    @Inject
    PaymentSessionExpiryJob job;

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private String productDefinitionId;

    /**
     * T_payment_transaction has FKs to T_contract and T_product_definition —
     * persist minimal parents first.
     */
    private PaymentTransaction newPendingTx(LocalDateTime createdAt) {
        String orgId = "expiry-test-org";

        if (productDefinitionId == null) {
            NonMultitenancyProductDefinition pd = new NonMultitenancyProductDefinition();
            pd.setId(UUID.randomUUID().toString());
            pd.setOrganisationId(orgId);
            pd.setProductCode("EXPIRY-TEST-" + UUID.randomUUID());
            pd.setBillingModel(NonMultitenancyProductDefinition.BillingModel.FIXED_PRICE);
            pd.setPaymentModel(NonMultitenancyProductDefinition.PaymentModel.PREPAID);
            pd.setProductValidFrom(java.time.LocalDate.now());
            pd.setStripeSecretKey("sk_test_expiry");
            em.persist(pd);
            productDefinitionId = pd.getId();
        }

        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(UUID.randomUUID().toString());
        contract.setOrganisationId(orgId);
        contract.setContractReference("EXPIRY-TEST-" + UUID.randomUUID());
        contract.setContractDate(java.time.LocalDate.now());
        contract.setGrandTotal(new BigDecimal("50.00"));
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setCreatedAt(createdAt);
        contract.setUpdatedAt(createdAt);
        em.persist(contract);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(orgId);
        tx.setContractId(contract.getId());
        tx.setProductDefinitionId(productDefinitionId);
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(UUID.randomUUID().toString());
        tx.setPspSessionId("cs_expiry_" + UUID.randomUUID());
        tx.setGrossAmount(new BigDecimal("50.00"));
        tx.setCurrency("EUR");
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCreatedAt(createdAt);
        tx.setUpdatedAt(createdAt);
        return tx;
    }

    @Test
    @Transactional
    void stalePendingTransactionIsMarkedExpired() {
        // Default sweep threshold is 48h (Stripe sessions live at most 24h).
        PaymentTransaction stale = newPendingTx(LocalDateTime.now().minusHours(49));
        em.persist(stale);
        em.flush();

        job.expireStalePendingSessions();
        em.clear();

        PaymentTransaction reloaded = em.find(PaymentTransaction.class, stale.getId());
        assertEquals(PaymentStatus.EXPIRED, reloaded.getStatus());
    }

    @Test
    @Transactional
    void freshPendingTransactionIsLeftAlone() {
        PaymentTransaction fresh = newPendingTx(LocalDateTime.now().minusHours(1));
        em.persist(fresh);
        em.flush();

        job.expireStalePendingSessions();
        em.clear();

        PaymentTransaction reloaded = em.find(PaymentTransaction.class, fresh.getId());
        assertEquals(PaymentStatus.PENDING, reloaded.getStatus());
    }

    @Test
    @Transactional
    void oldTerminalTransactionsAreRetained() {
        // Payment transactions are financial evidence and are never deleted,
        // regardless of age or status.
        PaymentTransaction oldTx = newPendingTx(LocalDateTime.now().minusDays(365));
        oldTx.setStatus(PaymentStatus.SUCCEEDED);
        em.persist(oldTx);
        em.flush();

        job.expireStalePendingSessions();
        em.clear();

        assertEquals(PaymentStatus.SUCCEEDED,
            em.find(PaymentTransaction.class, oldTx.getId()).getStatus());
    }

    @Test
    @Transactional
    void oldWebhookEventsAreDeleted() {
        PaymentTransaction tx = newPendingTx(LocalDateTime.now().minusDays(31));
        tx.setStatus(PaymentStatus.SUCCEEDED);
        em.persist(tx);

        WebhookEvent oldEvent = newWebhookEvent(tx.getId(),
            LocalDateTime.now().minusDays(31));
        em.persist(oldEvent);

        WebhookEvent recentEvent = newWebhookEvent(tx.getId(),
            LocalDateTime.now().minusDays(1));
        em.persist(recentEvent);

        // Unlinked events (UNMATCHED/REJECTED) are purged by the same cutoff.
        WebhookEvent oldUnlinked = newWebhookEvent(null,
            LocalDateTime.now().minusDays(31));
        em.persist(oldUnlinked);
        em.flush();

        job.expireStalePendingSessions();
        em.clear();

        assertEquals(null, em.find(WebhookEvent.class, oldEvent.getId()));
        assertEquals(null, em.find(WebhookEvent.class, oldUnlinked.getId()));
        assertEquals(tx.getId(),
            em.find(WebhookEvent.class, recentEvent.getId()).getPaymentTransactionId());
        // The linked transaction survives the webhook purge.
        assertEquals(PaymentStatus.SUCCEEDED,
            em.find(PaymentTransaction.class, tx.getId()).getStatus());
    }

    private WebhookEvent newWebhookEvent(String paymentTransactionId,
            LocalDateTime receivedAt) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOrganisationId("expiry-test-org");
        event.setPspIdentifier("stripe");
        event.setPspEventId("evt_" + UUID.randomUUID());
        event.setEventType("checkout.session.completed");
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setPaymentTransactionId(paymentTransactionId);
        event.setMatched(paymentTransactionId != null);
        event.setProcessingResult(WebhookEvent.ProcessingResult.PROCESSED);
        event.setRawPayload("{}");
        event.setReceivedAt(receivedAt);
        return event;
    }
}
