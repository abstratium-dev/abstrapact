package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Persists and deduplicates {@link WebhookEvent} records.
 *
 * <p>The unique constraint on {@code (psp_identifier, psp_event_id)} means a redelivered
 * event is detected as a duplicate. {@link #persistOrFindDuplicate} returns the persisted
 * event, or {@code null} if the event is a duplicate (already processed).
 */
@ApplicationScoped
public class WebhookEventService {

    @Inject
    EntityManager em;

    /**
     * Persists the webhook event. Returns the persisted event, or {@code null} if a row
     * with the same {@code (psp_identifier, psp_event_id)} already exists (duplicate).
     */
    @Transactional
    public WebhookEvent persistOrFindDuplicate(WebhookEvent event) {
        Long count = em.createQuery(
                "SELECT COUNT(w) FROM WebhookEvent w " +
                "WHERE w.pspIdentifier = :psp AND w.pspEventId = :eventId",
                Long.class)
            .setParameter("psp", event.getPspIdentifier())
            .setParameter("eventId", event.getPspEventId())
            .getSingleResult();
        if (count > 0) {
            return null;
        }
        em.persist(event);
        return event;
    }

    public boolean existsByPspEventId(String pspIdentifier, String pspEventId) {
        Long count = em.createQuery(
                "SELECT COUNT(w) FROM WebhookEvent w " +
                "WHERE w.pspIdentifier = :psp AND w.pspEventId = :eventId",
                Long.class)
            .setParameter("psp", pspIdentifier)
            .setParameter("eventId", pspEventId)
            .getSingleResult();
        return count > 0;
    }
}
