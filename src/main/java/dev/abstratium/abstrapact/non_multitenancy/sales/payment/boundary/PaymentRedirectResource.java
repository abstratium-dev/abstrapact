package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Optional;

/**
 * Browser redirect endpoints for Stripe Checkout.
 *
 * <p>Stripe redirects the customer's browser here after payment (success) or cancellation.
 * abstrapact then redirects to the per-product B2C redirect URL configured on the product
 * definition. This endpoint does <strong>not</strong> trigger state transitions — it only
 * reads the current payment/contract state.
 *
 * <p>Not behind OIDC — called by the customer's browser via a Stripe redirect.
 */
@Path("/public/payment")
@PermitAll
public class PaymentRedirectResource {

    @Inject
    PaymentService paymentService;

    @GET
    @Path("/success")
    @Operation(summary = "Browser redirect after successful Stripe payment")
    public Response success(@QueryParam("session_id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("session_id query parameter is required").build();
        }
        Optional<PaymentTransaction> txOpt = paymentService.findPaymentBySessionId(sessionId);
        if (txOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        PaymentTransaction tx = txOpt.get();

        Optional<NonMultitenancyProductDefinition> pdOpt =
            paymentService.resolveProductDefinitionForContract(tx.getContractId());
        String redirectUrl = pdOpt.map(NonMultitenancyProductDefinition::getPaymentSuccessRedirectUrl)
            .orElse(null);

        if (tx.getStatus() == PaymentStatus.SUCCEEDED) {
            if (redirectUrl != null && !redirectUrl.isBlank()) {
                return Response.status(Response.Status.FOUND)
                    .header("Location", replaceContractId(redirectUrl, tx.getContractId()))
                    .build();
            }
            return htmlPage("Payment successful",
                "Your payment has been received. Contract " + tx.getContractId() + ".");
        }

        if (tx.getStatus() == PaymentStatus.PENDING) {
            if (redirectUrl != null && !redirectUrl.isBlank()) {
                String url = replaceContractId(redirectUrl, tx.getContractId());
                String separator = url.contains("?") ? "&" : "?";
                return Response.status(Response.Status.FOUND)
                    .header("Location", url + separator + "status=processing")
                    .build();
            }
            return htmlPage("Payment is being processed",
                "Your payment is being processed. Contract " + tx.getContractId() + ".");
        }

        // FAILED or STALE — show a status page.
        String title = tx.getStatus() == PaymentStatus.STALE
            ? "Payment under review" : "Payment not completed";
        String body = tx.getStatus() == PaymentStatus.STALE
            ? "Your payment is under manual review. Contract " + tx.getContractId() + "."
            : "Your payment was not completed. Contract " + tx.getContractId() + ".";
        return htmlPage(title, body);
    }

    @GET
    @Path("/cancel")
    @Operation(summary = "Browser redirect after cancelled Stripe payment")
    public Response cancel(@QueryParam("session_id") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("session_id query parameter is required").build();
        }
        Optional<PaymentTransaction> txOpt = paymentService.findPaymentBySessionId(sessionId);
        if (txOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        PaymentTransaction tx = txOpt.get();

        Optional<NonMultitenancyProductDefinition> pdOpt =
            paymentService.resolveProductDefinitionForContract(tx.getContractId());
        String redirectUrl = pdOpt.map(NonMultitenancyProductDefinition::getPaymentCancelRedirectUrl)
            .orElse(null);

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return Response.status(Response.Status.FOUND)
                .header("Location", replaceContractId(redirectUrl, tx.getContractId()))
                .build();
        }
        return htmlPage("Payment cancelled",
            "Your payment was cancelled. Contract " + tx.getContractId()
                + " remains awaiting payment.");
    }

    // ==================== helpers ====================

    private static String replaceContractId(String url, String contractId) {
        return url.replace("{contractId}", contractId);
    }

    private static Response htmlPage(String title, String body) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>"
            + escape(title) + "</title></head><body><h1>" + escape(title)
            + "</h1><p>" + escape(body) + "</p></body></html>";
        return Response.ok(html, MediaType.TEXT_HTML_TYPE).build();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
