package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

/**
 * The cached result of an idempotent operation.
 *
 * <p>Stored in {@link IdempotencyService} so that retries with the same
 * {@code Idempotency-Key} replay the same HTTP response without re-executing
 * the business logic.
 *
 * @param statusCode the HTTP status code returned by the processor
 * @param body       the serialized response body returned by the processor
 */
public record ProcessedResponse(int statusCode, String body) {
}
