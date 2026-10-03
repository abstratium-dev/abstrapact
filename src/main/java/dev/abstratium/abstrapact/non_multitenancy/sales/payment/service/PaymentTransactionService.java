package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CRUD operations for {@link PaymentTransaction} records.
 */
@ApplicationScoped
public class PaymentTransactionService {

    @Inject
    EntityManager em;

    @Transactional
    public void persist(PaymentTransaction tx) {
        em.persist(tx);
    }

    /**
     * Persists a new {@code PENDING} transaction in its own transaction that commits
     * immediately (REQUIRES_NEW), before any PSP call is made.
     *
     * <p>Splitting the persistence of the payment attempt from the Stripe session
     * creation means a concurrent duplicate insert hits
     * {@code UQ_payment_transaction_pending_contract} <em>before</em> the losing
     * request ever calls Stripe — no orphaned Checkout Session is created.
     * The flush is eager so the constraint violation surfaces here, not at commit.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PaymentTransaction persistNewPending(PaymentTransaction tx) {
        em.persist(tx);
        em.flush();
        return tx;
    }

    /**
     * Stores the PSP session id and checkout URL on an existing transaction in its
     * own transaction (REQUIRES_NEW). Called after the PSP session was created, so
     * the session data is committed independently of the caller's transaction.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void storeSession(String txId, String pspSessionId, String checkoutUrl) {
        PaymentTransaction tx = em.find(PaymentTransaction.class, txId);
        if (tx != null) {
            tx.setPspSessionId(pspSessionId);
            tx.setCheckoutUrl(checkoutUrl);
            tx.setUpdatedAt(java.time.LocalDateTime.now());
            em.merge(tx);
        }
    }

    public Optional<PaymentTransaction> findById(String id) {
        return Optional.ofNullable(em.find(PaymentTransaction.class, id));
    }

    public Optional<PaymentTransaction> findByCorrelationId(String correlationId) {
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.correlationId = :cid",
                PaymentTransaction.class)
            .setParameter("cid", correlationId)
            .getResultStream()
            .findFirst();
    }

    public Optional<PaymentTransaction> findByPspSessionId(String pspSessionId) {
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.pspSessionId = :sid",
                PaymentTransaction.class)
            .setParameter("sid", pspSessionId)
            .getResultStream()
            .findFirst();
    }

    @Transactional
    public PaymentTransaction updateStatus(String id, PaymentTransaction.PaymentStatus status) {
        PaymentTransaction tx = em.find(PaymentTransaction.class, id);
        if (tx != null) {
            tx.setStatus(status);
            em.merge(tx);
        }
        return tx;
    }

    @Transactional
    public PaymentTransaction updateFeeAndRef(String id, java.math.BigDecimal feeAmount, String pspTransactionRef) {
        PaymentTransaction tx = em.find(PaymentTransaction.class, id);
        if (tx != null) {
            if (feeAmount != null) {
                tx.setFeeAmount(feeAmount);
                tx.setNetAmount(tx.getGrossAmount().subtract(feeAmount));
            }
            if (pspTransactionRef != null) {
                tx.setPspTransactionRef(pspTransactionRef);
            }
            em.merge(tx);
        }
        return tx;
    }

    /**
     * Returns all SUCCEEDED transactions whose {@code updatedAt} falls in
     * {@code [from, to]} (inclusive) for the given organisation, ordered by updatedAt.
     * Used by the CSV export endpoint.
     */
    public List<PaymentTransaction> findSucceededInRange(
            String organisationId, LocalDate from, LocalDate to) {
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t " +
                "WHERE t.organisationId = :orgId " +
                "AND t.status = 'SUCCEEDED' " +
                "AND t.updatedAt >= :from " +
                "AND t.updatedAt < :toNext " +
                "ORDER BY t.updatedAt",
                PaymentTransaction.class)
            .setParameter("orgId", organisationId)
            .setParameter("from", from.atStartOfDay())
            .setParameter("toNext", to.plusDays(1).atStartOfDay())
            .getResultList();
    }
}
