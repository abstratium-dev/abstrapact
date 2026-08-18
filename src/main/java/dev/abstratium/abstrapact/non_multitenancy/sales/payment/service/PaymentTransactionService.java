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
