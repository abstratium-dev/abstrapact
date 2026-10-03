package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentService;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PSPSelector;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;

/**
 * Stripe webhook endpoint.
 *
 * <p>This endpoint is <strong>not</strong> behind OIDC authentication — it is called by
 * Stripe, not by an authenticated user. Signature verification (per-product webhook
 * secret) is the authentication mechanism.
 *
 * <p>Every webhook is recorded in {@code T_webhook_event} — matched, unmatched, stale,
 * duplicate, and rejected events alike — providing a complete audit trail. Only webhooks
 * whose payload exceeds the configurable size limit
 * ({@code abstrapact.payment.webhook.max-payload-size-bytes}) are not recorded; those are
 * logged via the JBoss logger only.
 *
 * <p>The handler responds {@code 200} to Stripe for matched, unmatched, stale, and
 * duplicate events alike so the event is not retried. Signature verification failure and
 * oversized payloads return {@code 400}.
 */
@Path("/public/payment/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class PaymentWebhookResource {

    private static final Logger log = Logger.getLogger(PaymentWebhookResource.class);

    @Inject
    PaymentService paymentService;

    @Inject
    PSPSelector pspSelector;

    @POST
    @Operation(summary = "Receive a PSP webhook event")
    public Response handleWebhook(String payload, @HeaderParam("Stripe-Signature") String signature) {
        long start = System.currentTimeMillis();
        int payloadLength = payload == null ? 0 : payload.length();
        log.debugf("Received webhook (payload length=%d, signature present=%s)",
            payloadLength,
            String.valueOf(signature != null && !signature.isBlank()));

        // 1. Check payload size — oversized webhooks are logged but not recorded.
        if (payloadLength > paymentService.getMaxPayloadSizeBytes()) {
            log.warnf("Webhook payload exceeds max size (%d > %d bytes) — not recording",
                payloadLength, paymentService.getMaxPayloadSizeBytes());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Webhook payload exceeds maximum size")
                .build();
        }

        try {
            PaymentEventResult result = pspSelector.getActive().processWebhookEvent(payload, signature);
            log.infof("Webhook processed: type=%s, correlationId=%s, matched=%s, status=%s",
                result.getEventType(),
                result.getCorrelationId(),
                result.isMatched(),
                result.getStatus());

            paymentService.handlePaymentResult(result);
            log.infof("Webhook handled successfully for correlationId=%s in %s milliseconds", result.getCorrelationId(), System.currentTimeMillis() - start);
            return Response.ok().build();
        } catch (WebApplicationException e) {
            String reason = extractRejectionReason(e);
            log.warnf(e, "Webhook rejected with status %d: %s",
                e.getResponse().getStatus(), reason);
            // Record the rejected event for audit/debugging.
            paymentService.recordRejectedEvent(payload, reason);
            return e.getResponse();
        } catch (Exception e) {
            // A concurrent delivery of the same event can race past the
            // existsByPspEventId check and lose on UQ_webhook_event_psp_event at
            // flush/commit time. The winner already committed the event and its
            // state changes, so this delivery is a true duplicate: acknowledge it
            // with 200 instead of a 500 that would trigger Stripe retries.
            if (isDuplicateEventViolation(e)) {
                log.infof("Duplicate webhook event lost the insert race on "
                    + "UQ_webhook_event_psp_event — acknowledging with 200");
                return Response.ok().build();
            }
            log.errorf(e, "Unexpected error processing webhook");
            // Record the rejected event for audit/debugging.
            paymentService.recordRejectedEvent(payload, "Unexpected error: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    /**
     * Walks the cause chain looking for a {@code UQ_webhook_event_psp_event}
     * unique-constraint violation (which may surface as a Hibernate
     * {@code ConstraintViolationException} or be wrapped in a
     * {@code PersistenceException}/{@code RollbackException} from commit).
     */
    private static boolean isDuplicateEventViolation(Throwable e) {
        while (e != null) {
            String msg = e.getMessage();
            if (msg != null && msg.toUpperCase().contains("UQ_WEBHOOK_EVENT")) {
                return true;
            }
            if (e instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toUpperCase().contains("WEBHOOK_EVENT")) {
                    return true;
                }
            }
            e = e.getCause();
        }
        return false;
    }

    /**
     * Extracts a human-readable rejection reason from a {@link WebApplicationException}.
     * The exception message is usually just the HTTP status (e.g. "HTTP 400 Bad Request"),
     * so the response entity is preferred when available.
     */
    private static String extractRejectionReason(WebApplicationException e) {
        Object entity = e.getResponse().getEntity();
        if (entity != null) {
            return entity.toString();
        }
        return e.getMessage();
    }
}
