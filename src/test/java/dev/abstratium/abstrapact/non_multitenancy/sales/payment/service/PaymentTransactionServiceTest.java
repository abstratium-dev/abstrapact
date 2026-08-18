package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link PaymentTransactionService} CRUD operations and queries.
 */
@QuarkusTest
class PaymentTransactionServiceTest {

    @Inject
    PaymentTransactionService service;

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    private String contractId;
    private PaymentTransaction tx;

    @BeforeEach
    @Transactional
    void setUp() {
        contractId = UUID.randomUUID().toString();
        String orgId = "test-org-tx";

        // Create a contract (FK constraint on payment_transaction.contract_id)
        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(orgId);
        contract.setContractReference("TX-TEST-" + UUID.randomUUID());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setGrandTotal(new BigDecimal("12.00"));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        tx = newTransaction("corr-1", contractId, orgId, PaymentTransaction.PaymentStatus.PENDING);
        service.persist(tx);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    void findByIdReturnsPersistedTransaction() {
        Optional<PaymentTransaction> found = service.findById(tx.getId());
        assertTrue(found.isPresent());
        assertEquals("corr-1", found.get().getCorrelationId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<PaymentTransaction> found = service.findById("unknown-id");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByCorrelationIdReturnsTransaction() {
        Optional<PaymentTransaction> found = service.findByCorrelationId("corr-1");
        assertTrue(found.isPresent());
        assertEquals(tx.getId(), found.get().getId());
    }

    @Test
    void findByCorrelationIdReturnsEmptyForUnknown() {
        Optional<PaymentTransaction> found = service.findByCorrelationId("unknown-corr");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByPspSessionIdReturnsTransaction() {
        PaymentTransaction tx2 = newTransaction("corr-2", contractId, "test-org-tx",
            PaymentTransaction.PaymentStatus.PENDING);
        tx2.setPspSessionId("cs_test_456");
        service.persist(tx2);

        Optional<PaymentTransaction> found = service.findByPspSessionId("cs_test_456");
        assertTrue(found.isPresent());
        assertEquals("corr-2", found.get().getCorrelationId());
    }

    @Test
    void findByPspSessionIdReturnsEmptyForUnknown() {
        Optional<PaymentTransaction> found = service.findByPspSessionId("unknown-session");
        assertTrue(found.isEmpty());
    }

    @Test
    void updateStatusChangesStatus() {
        PaymentTransaction updated = service.updateStatus(tx.getId(),
            PaymentTransaction.PaymentStatus.SUCCEEDED);
        assertNotNull(updated);
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, updated.getStatus());

        Optional<PaymentTransaction> found = service.findById(tx.getId());
        assertEquals(PaymentTransaction.PaymentStatus.SUCCEEDED, found.get().getStatus());
    }

    @Test
    void updateStatusReturnsNullForUnknownId() {
        PaymentTransaction updated = service.updateStatus("unknown",
            PaymentTransaction.PaymentStatus.SUCCEEDED);
        assertNull(updated);
    }

    @Test
    void updateFeeAndRefSetsFeeNetAndRef() {
        PaymentTransaction updated = service.updateFeeAndRef(tx.getId(),
            new BigDecimal("0.59"), "pi_test_123");
        assertNotNull(updated);
        assertEquals(0, new BigDecimal("0.59").compareTo(updated.getFeeAmount()));
        assertEquals(0, new BigDecimal("11.41").compareTo(updated.getNetAmount())); // 12.00 - 0.59
        assertEquals("pi_test_123", updated.getPspTransactionRef());
    }

    @Test
    void updateFeeAndRefWithNullFeeOnlyUpdatesRef() {
        PaymentTransaction updated = service.updateFeeAndRef(tx.getId(), null, "pi_ref_only");
        assertNotNull(updated);
        assertEquals("pi_ref_only", updated.getPspTransactionRef());
        assertNull(updated.getFeeAmount());
    }

    @Test
    void updateFeeAndRefReturnsNullForUnknownId() {
        PaymentTransaction updated = service.updateFeeAndRef("unknown",
            BigDecimal.ONE, "pi_x");
        assertNull(updated);
    }

    @Test
    void findSucceededInRangeReturnsOnlySucceededInDateRange() {
        // tx is PENDING — should not be returned
        assertTrue(service.findSucceededInRange("test-org-tx",
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(1)).isEmpty());

        // Create a SUCCEEDED transaction
        PaymentTransaction tx2 = newTransaction("corr-succ", contractId, "test-org-tx",
            PaymentTransaction.PaymentStatus.SUCCEEDED);
        service.persist(tx2);

        var results = service.findSucceededInRange("test-org-tx",
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(1));
        assertEquals(1, results.size());
        assertEquals("corr-succ", results.get(0).getCorrelationId());
    }

    @Test
    void findSucceededInRangeFiltersByOrganisation() {
        PaymentTransaction tx2 = newTransaction("corr-other-org", contractId, "org-2",
            PaymentTransaction.PaymentStatus.SUCCEEDED);
        service.persist(tx2);

        var results = service.findSucceededInRange("test-org-tx",
            LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(1));
        assertTrue(results.isEmpty());
    }

    // ==================== helpers ====================

    private PaymentTransaction newTransaction(String corrId, String contractId, String orgId,
            PaymentTransaction.PaymentStatus status) {
        PaymentTransaction t = new PaymentTransaction();
        t.setId(UUID.randomUUID().toString());
        t.setOrganisationId(orgId);
        t.setContractId(contractId);
        t.setPspIdentifier("stripe");
        t.setCorrelationId(corrId);
        t.setGrossAmount(new BigDecimal("12.00"));
        t.setCurrency("EUR");
        t.setStatus(status);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }
}
