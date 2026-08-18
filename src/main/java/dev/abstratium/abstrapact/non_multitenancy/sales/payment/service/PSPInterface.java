package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;

/**
 * PSP-agnostic interface abstracting Payment Service Provider operations.
 *
 * <p>Each PSP implementation provides its own CDI bean; the active PSP is selected via
 * configuration ({@code abstrapact.payment.psp}) by {@link PSPSelector}. The initial
 * implementation is {@code StripePSPService} (Stripe Checkout Sessions).
 *
 * <p>See {@code docs/DESIGN_OF_PAYMENT.md}.
 */
public interface PSPInterface {

    /**
     * Creates a payment for the given contract and returns a URL the customer can be
     * redirected to. PSP credentials and redirect URLs are resolved from the product
     * definition and passed in the request.
     *
     * @param request the payment creation request (amount, currency, credentials, ...)
     * @return the created payment response (checkout URL + PSP session id)
     */
    CreatePaymentResponse createPayment(CreatePaymentRequest request);

    /**
     * Processes a webhook event. The implementation must determine the correct webhook
     * secret for signature verification (see Webhook Handling in the design doc).
     *
     * <p>Returns an unmatched result if the event does not correspond to a known payment
     * transaction. Throws a {@link jakarta.ws.rs.WebApplicationException} with status 400
     * if the signature cannot be verified against any known product webhook secret.
     *
     * @param payload   the raw webhook payload body
     * @param signature the PSP signature header (e.g. Stripe's {@code Stripe-Signature})
     * @return the result of parsing/verifying the event (matched status, correlation id, ...)
     */
    PaymentEventResult processWebhookEvent(String payload, String signature);

    /**
     * @return the PSP identifier (e.g. {@code "stripe"}, {@code "paypal"}). Used by
     *     {@link PSPSelector} to pick the active implementation and recorded on every
     *     {@code PaymentTransaction} and {@code WebhookEvent}.
     */
    String getPspIdentifier();
}
