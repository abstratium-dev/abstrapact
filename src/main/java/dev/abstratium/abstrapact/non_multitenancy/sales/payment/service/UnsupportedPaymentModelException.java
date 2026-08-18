package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Thrown by {@code SalesProcessService.triggerPaymentHandling} when a contract's payment
 * model is not supported by the current implementation (e.g. {@code POSTPAID} before
 * periodic invoicing is implemented).
 *
 * <p>Mapped to HTTP {@code 422 Unprocessable Entity} by
 * {@link dev.abstratium.core.filter.UnsupportedPaymentModelExceptionMapper}. The contract
 * remains in {@code APPROVED} — no transition, no payment created.
 */
public class UnsupportedPaymentModelException extends WebApplicationException {

    public UnsupportedPaymentModelException(String message) {
        super(Response.status(422).entity(message).build());
    }
}
