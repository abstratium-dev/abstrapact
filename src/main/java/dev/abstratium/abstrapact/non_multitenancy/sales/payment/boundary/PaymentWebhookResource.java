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
 * <p>Every verified event is recorded in {@code T_webhook_event}. The handler responds
 * {@code 200} to Stripe for matched, unmatched, stale, and duplicate events alike so the
 * event is not retried. Only signature verification failure returns {@code 400}.
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
        log.debugf("Received webhook (payload length=%d, signature present=%s)",
            payload == null ? 0 : payload.length(),
            String.valueOf(signature != null && !signature.isBlank()));

        try {
            PaymentEventResult result = pspSelector.getActive().processWebhookEvent(payload, signature);
            log.infof("Webhook processed: type=%s, correlationId=%s, matched=%s, status=%s",
                result.getEventType(),
                result.getCorrelationId(),
                result.isMatched(),
                result.getStatus());

            paymentService.handlePaymentResult(result);
            log.debugf("Webhook handled successfully for correlationId=%s", result.getCorrelationId());
            return Response.ok().build();
        } catch (WebApplicationException e) {
            log.warnf(e, "Webhook rejected with status %d: %s",
                e.getResponse().getStatus(),
                e.getMessage());
            return e.getResponse();
        } catch (Exception e) {
            log.errorf(e, "Unexpected error processing webhook");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
