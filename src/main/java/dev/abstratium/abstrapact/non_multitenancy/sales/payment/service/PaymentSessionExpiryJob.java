package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that marks {@code PENDING} payment transactions whose PSP session
 * has outlived its lifetime as {@code EXPIRED}.
 *
 * <p>Stripe Checkout Sessions expire after 24 hours by default. The
 * {@code checkout.session.expired} webhook is the primary expiry mechanism; this
 * job is the backstop in case that webhook is lost (the "at least once" payment
 * guarantee requires that a dead session can always be replaced via
 * {@code POST /api/public/sales/contracts/{id}/retry-payment}).
 *
 * <p>See {@code docs/DESIGN_OF_IDEMPOTENCY.md}.
 */
@ApplicationScoped
public class PaymentSessionExpiryJob {

    private static final Logger log = Logger.getLogger(PaymentSessionExpiryJob.class);

    @Inject
    EntityManager em;

    /**
     * Stripe Checkout Sessions can live at most 24 hours ({@code expires_at}
     * range is 30 minutes–24 hours, default 24). The sweep threshold must be
     * strictly greater than that so a session can never be marked EXPIRED here
     * while Stripe would still let the customer pay. Configured values below
     * this minimum are clamped to it.
     */
    static final long MIN_SESSION_TTL_HOURS = 25;

    @ConfigProperty(name = "abstrapact.payment.session-ttl-hours",
        defaultValue = "48")
    long sessionTtlHours;

    @ConfigProperty(name = "abstrapact.payment.transaction-retention-days",
        defaultValue = "30")
    long transactionRetentionDays;

    @Scheduled(every = "1h")
    @Transactional
    public void expireStalePendingSessions() {
        long ttlHours = sessionTtlHours;
        if (ttlHours < MIN_SESSION_TTL_HOURS) {
            log.warnf("abstrapact.payment.session-ttl-hours=%d is below Stripe's maximum "
                + "session lifetime (24h); clamping to %d", ttlHours, MIN_SESSION_TTL_HOURS);
            ttlHours = MIN_SESSION_TTL_HOURS;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ttlHours);
        int expired = em.createQuery(
                "UPDATE PaymentTransaction t " +
                "SET t.status = :expired, t.updatedAt = :now " +
                "WHERE t.status = :pending AND t.createdAt < :cutoff")
            .setParameter("expired", PaymentStatus.EXPIRED)
            .setParameter("pending", PaymentStatus.PENDING)
            .setParameter("now", LocalDateTime.now())
            .setParameter("cutoff", cutoff)
            .executeUpdate();

        if (expired > 0) {
            log.infof("Marked %d stale PENDING payment transaction(s) as EXPIRED", expired);
        }

        deleteOldTerminalTransactions();
    }

    /**
     * Deletes payment transactions in a terminal state
     * ({@code SUCCEEDED}, {@code FAILED}, {@code STALE}, {@code EXPIRED}) that have
     * not been modified for {@code abstrapact.payment.transaction-retention-days}
     * (default 30 days). PSP session expiry is owned by Stripe (max 24h session
     * TTL + {@code checkout.session.expired} webhook), so terminal rows are only
     * retained for reconciliation and troubleshooting.
     *
     * <p>Note: {@code PaymentTransaction} is {@code @Audited} — Envers history in
     * {@code T_payment_transaction_AUD} is kept, so this keeps the live table
     * small but does not reclaim audit storage.
     */
    void deleteOldTerminalTransactions() {
        LocalDateTime retentionCutoff =
            LocalDateTime.now().minusDays(transactionRetentionDays);
        int deleted = em.createQuery(
                "DELETE FROM PaymentTransaction t " +
                "WHERE t.status IN (:terminal) AND t.updatedAt < :cutoff")
            .setParameter("terminal", List.of(
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED,
                PaymentStatus.STALE, PaymentStatus.EXPIRED))
            .setParameter("cutoff", retentionCutoff)
            .executeUpdate();

        if (deleted > 0) {
            log.infof("Deleted %d terminal payment transaction(s) older than %d days",
                deleted, transactionRetentionDays);
        }
    }
}
