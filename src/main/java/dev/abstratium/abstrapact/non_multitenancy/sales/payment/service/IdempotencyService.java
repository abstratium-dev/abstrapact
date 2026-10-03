package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.IdempotencyRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotency service for deduplicating mutating REST requests.
 *
 * <p>When a client sends a mutating request with an {@code Idempotency-Key} header,
 * the server caches the result. Retries with the same key replay the cached result
 * without re-executing the business logic.
 *
 * <p>Concurrency safety is provided by a database unique constraint on
 * {@code (idempotency_key, scope)}. The first writer wins; others replay.
 *
 * <p>See {@code docs/DESIGN_OF_IDEMPOTENCY.md}.
 */
@ApplicationScoped
public class IdempotencyService {

    @Inject
    EntityManager em;

    @Transactional
    public Optional<IdempotencyRecord> find(String key, String scope) {
        return em.createQuery(
                "SELECT r FROM IdempotencyRecord r WHERE r.key = :key AND r.scope = :scope",
                IdempotencyRecord.class)
            .setParameter("key", key)
            .setParameter("scope", scope)
            .getResultStream()
            .findFirst();
    }

    /**
     * Executes the processor if no record exists for the given key + scope.
     * If a record exists, validates the fingerprint and returns the cached result.
     *
     * <p>On a constraint violation (race condition where another transaction won),
     * rolls back, finds the existing record, and returns it.
     *
     * @param key               the idempotency key from the client
     * @param scope             the operation scope (e.g. {@code "contract_accept"})
     * @param scopeId           the account id or contract id
     * @param requestFingerprint SHA-256 of the request payload
     * @param processor         the business logic to execute on first call
     * @return the idempotency record (newly created or cached)
     * @throws WebApplicationException with 422 if the key was reused with a different payload
     */
    @Transactional
    public IdempotencyRecord execute(
            String key,
            String scope,
            String scopeId,
            String requestFingerprint,
            Supplier<ProcessedResponse> processor) {

        Optional<IdempotencyRecord> existing = find(key, scope);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
                // Expired record: remove it and treat the request as new.
                em.remove(record);
                em.flush();
            } else {
                if (!record.getRequestFingerprint().equals(requestFingerprint)) {
                    throw new WebApplicationException(
                        Response.status(422)
                            .entity("Idempotency key reused with different payload")
                            .build());
                }
                return record;
            }
        }

        ProcessedResponse response;
        try {
            response = processor.get();
        } catch (WebApplicationException e) {
            // If the processor returns a 4xx error, we still cache it to prevent
            // a thundering herd of retries all hitting the same validation error.
            // The exception is re-thrown after caching.
            if (e.getResponse().getStatus() >= 400 && e.getResponse().getStatus() < 500) {
                persistOrThrowRace(key, scope, scopeId, requestFingerprint,
                    e.getResponse().getStatus(),
                    e.getResponse().getEntity() != null ? e.getResponse().getEntity().toString() : null);
            }
            throw e;
        }

        return persistOrThrowRace(key, scope, scopeId, requestFingerprint,
            response.statusCode(), response.body());
    }

    /**
     * Replays the winning record after an {@link IdempotencyRaceException}.
     *
     * <p>Must be called in a NEW transaction after the losing transaction has
     * rolled back. A UNIQUE constraint violation observed during {@code flush}
     * implies the competing transaction has already committed, so the winning
     * record is guaranteed to be visible here.
     *
     * @throws WebApplicationException with 422 if the winner used a different fingerprint
     */
    @Transactional
    public IdempotencyRecord replay(String key, String scope, String requestFingerprint) {
        IdempotencyRecord record = find(key, scope)
            .orElseThrow(() -> new IllegalStateException(
                "Idempotency record not found after race for key '" + key + "'"));
        if (!record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new WebApplicationException(
                Response.status(422)
                    .entity("Idempotency key reused with different payload")
                    .build());
        }
        return record;
    }

    /**
     * Persists a record, flushing eagerly so that a concurrent insert with the
     * same (key, scope) surfaces here instead of at commit time. On a unique
     * constraint violation the transaction is already rollback-only, so an
     * {@link IdempotencyRaceException} is thrown for the caller to recover in a
     * fresh transaction.
     */
    private IdempotencyRecord persistOrThrowRace(
            String key, String scope, String scopeId,
            String fingerprint, int statusCode, String body) {
        IdempotencyRecord record = createRecord(key, scope, scopeId, fingerprint, statusCode, body);
        try {
            em.persist(record);
            em.flush();
        } catch (org.hibernate.exception.ConstraintViolationException e) {
            // Hibernate throws its own ConstraintViolationException (a
            // HibernateException, not a PersistenceException) from flush.
            if (isIdempotencyConstraintViolation(e)) {
                throw new IdempotencyRaceException(key, scope);
            }
            throw e;
        } catch (PersistenceException e) {
            if (isConstraintViolation(e)) {
                throw new IdempotencyRaceException(key, scope);
            }
            throw e;
        }
        return record;
    }

    /**
     * Returns true only if the violation hit the {@code UQ_idempotency_key_scope}
     * constraint. Other unique violations (e.g. on {@code T_payment_transaction})
     * are real errors and must not be treated as an idempotency race.
     */
    private boolean isIdempotencyConstraintViolation(
            org.hibernate.exception.ConstraintViolationException e) {
        String constraintName = e.getConstraintName();
        if (constraintName != null) {
            return constraintName.toUpperCase().contains("IDEMPOTENCY");
        }
        String message = e.getMessage();
        return message != null && message.toUpperCase().contains("IDEMPOTENCY");
    }

    private IdempotencyRecord createRecord(
            String key, String scope, String scopeId,
            String fingerprint, int statusCode, String body) {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID().toString());
        record.setKey(key);
        record.setScope(scope);
        record.setScopeId(scopeId);
        record.setRequestFingerprint(fingerprint);
        record.setStatusCode(statusCode);
        record.setResponseBody(body);
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusHours(24));
        return record;
    }

    private boolean isConstraintViolation(PersistenceException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return isIdempotencyConstraintViolation(cve);
            }
            t = t.getCause();
        }
        return false;
    }
}
