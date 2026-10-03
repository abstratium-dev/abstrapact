package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

/**
 * Thrown when a concurrent request with the same idempotency key won the race
 * to persist its {@code T_idempotency_record} row.
 *
 * <p>The current transaction is already doomed (marked rollback-only by JPA
 * after the constraint violation), so recovery cannot happen inside it.
 * Callers must catch this exception and re-read the winning record in a new
 * transaction via {@link IdempotencyService#replay}.
 */
public class IdempotencyRaceException extends RuntimeException {

    public IdempotencyRaceException(String key, String scope) {
        super("Concurrent request with idempotency key '" + key
            + "' in scope '" + scope + "' already committed");
    }
}
