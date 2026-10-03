package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * Scheduled job that purges expired idempotency records.
 *
 * <p>Idempotency records are valid for 24 hours (matching Stripe's cache window).
 * After expiry they are eligible for deletion. A retry with an expired key is
 * treated as a new request.
 *
 * <p>See {@code docs/DESIGN_OF_IDEMPOTENCY.md}.
 */
@ApplicationScoped
public class IdempotencyCleanupJob {

    private static final Logger log = Logger.getLogger(IdempotencyCleanupJob.class);

    @Inject
    EntityManager em;

    @Scheduled(every = "1h")
    @Transactional
    public void cleanup() {
        int deleted = em.createQuery(
                "DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
            .setParameter("now", LocalDateTime.now())
            .executeUpdate();

        if (deleted > 0) {
            log.infof("Deleted %d expired idempotency records", deleted);
        }
    }
}
