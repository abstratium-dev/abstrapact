package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
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
    void oldTerminalTransactionsAreDeleted() {
        // Terminal tx untouched for >30 days → deleted by the retention sweep.
        PaymentTransaction oldFailed = newPendingTx(LocalDateTime.now().minusDays(31));
        oldFailed.setStatus(PaymentStatus.FAILED);
        em.persist(oldFailed);

        // Recent terminal tx → kept.
        PaymentTransaction recentFailed = newPendingTx(LocalDateTime.now().minusDays(1));
        recentFailed.setStatus(PaymentStatus.FAILED);
        em.persist(recentFailed);
        em.flush();

        job.expireStalePendingSessions();
        em.clear();

        assertEquals(null, em.find(PaymentTransaction.class, oldFailed.getId()));
        assertEquals(PaymentStatus.FAILED,
            em.find(PaymentTransaction.class, recentFailed.getId()).getStatus());
    }
}
