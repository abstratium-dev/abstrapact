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
 * <p>The same schedule also purges old {@code WebhookEvent} rows — transient
 * logs of Stripe callbacks. {@code PaymentTransaction} rows are never deleted:
 * they are the application's financial evidence that a payment was made.
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

    /**
     * Webhook events are a transient log of Stripe callbacks used for
     * processing, deduplication, and short-term troubleshooting. Once a
     * transaction is terminal the raw payload has no remaining value, so
     * events older than this retention window are deleted. The
     * {@code PaymentTransaction} rows themselves — the financial evidence —
     * are never deleted by this job.
     */
    @ConfigProperty(name = "abstrapact.payment.webhook.retention-days",
        defaultValue = "30")
    long webhookRetentionDays;

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

        deleteOldWebhookEvents();
    }

    /**
     * Deletes webhook events received more than
     * {@code abstrapact.payment.webhook.retention-days} ago (default 30 days).
     * Webhook events are transient processing/deduplication logs; the payment
     * transactions they refer to are retained indefinitely as the financial
     * record. Unlinked events ({@code paymentTransactionId IS NULL}, e.g.
     * {@code UNMATCHED}/{@code REJECTED}) are deleted by the same cutoff.
     */
    void deleteOldWebhookEvents() {
        LocalDateTime retentionCutoff =
            LocalDateTime.now().minusDays(webhookRetentionDays);
        int deleted = em.createQuery(
                "DELETE FROM WebhookEvent w WHERE w.receivedAt < :cutoff")
            .setParameter("cutoff", retentionCutoff)
            .executeUpdate();

        if (deleted > 0) {
            log.infof("Deleted %d webhook event(s) older than %d days",
                deleted, webhookRetentionDays);
        }
    }
}
