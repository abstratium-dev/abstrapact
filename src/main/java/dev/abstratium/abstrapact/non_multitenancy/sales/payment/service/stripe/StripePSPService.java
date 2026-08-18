package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.stripe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PSPInterface;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Stripe implementation of {@link PSPInterface} using Stripe Checkout Sessions to generate
 * hosted payment pages.
 *
 * <p>Each product has its own Stripe account, so a {@link StripeClient} is created per
 * payment request (with the product's secret key) rather than as a singleton. The Stripe
 * API base URL is configurable ({@code abstrapact.payment.stripe.api-base}) so tests can
 * point it at a Wiremock server.
 *
 * <p>Webhook signature verification uses per-product webhook secrets. The secret is
 * resolved by looking up the {@code PaymentTransaction} → contract → product definition
 * → {@code stripe_webhook_secret}. For unmatched events (no correlation id or unknown
 * correlation id), all configured product webhook secrets are tried.
 *
 * <p>See {@code docs/DESIGN_OF_PAYMENT.md}.
 */
@ApplicationScoped
public class StripePSPService implements PSPInterface {

    public static final String PSP_IDENTIFIER = "stripe";
    static final String METADATA_KEY_CORRELATION_ID = "correlation_id";

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "abstrapact.payment.stripe.success-url")
    String successUrl;

    @ConfigProperty(name = "abstrapact.payment.stripe.cancel-url")
    String cancelUrl;

    @ConfigProperty(name = "abstrapact.payment.stripe.api-base",
        defaultValue = "https://api.stripe.com")
    String apiBase;

    @Override
    public String getPspIdentifier() {
        return PSP_IDENTIFIER;
    }

    // ==================== Payment creation ====================

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        StripeClient client = newClient(request.getStripeSecretKey());

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .putMetadata(METADATA_KEY_CORRELATION_ID, request.getCorrelationId())
            .setClientReferenceId(request.getContractId())
            .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                .putMetadata(METADATA_KEY_CORRELATION_ID, request.getCorrelationId())
                .build())
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setUnitAmountDecimal(toMinorUnits(request.getAmount()))
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(request.getDescription())
                        .build())
                    .build())
                .build())
            .build();

        try {
            Session session = client.v1().checkout().sessions().create(params);
            return new CreatePaymentResponse(session.getUrl(), session.getId());
        } catch (StripeException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Stripe checkout session creation failed: " + e.getMessage())
                    .build());
        }
    }

    // ==================== Webhook handling ====================

    @Override
    public PaymentEventResult processWebhookEvent(String payload, String signature) {
        // 1. Parse the raw payload to extract the correlation id and session id
        //    (untrusted at this point — used only to look up the webhook secret).
        String correlationId = extractCorrelationId(payload);
        String sessionId = extractSessionId(payload);

        // 2. Resolve the webhook secret for signature verification.
        //    Try by correlation id first, then by session id.
        String webhookSecret = resolveWebhookSecret(correlationId, sessionId);

        // 3. Verify the signature with the resolved secret.
        //    If no secret could be resolved, reject the webhook — we cannot verify it.
        if (webhookSecret == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Webhook signature verification failed: no matching product")
                    .build());
        }
        Event event = verify(payload, signature, webhookSecret);

        // 4. Map the verified event to a PaymentEventResult.
        return mapEvent(event, payload, correlationId);
    }

    // ==================== helpers: client + amounts ====================

    private StripeClient newClient(String secretKey) {
        return StripeClient.builder()
            .setApiKey(secretKey)
            .setApiBase(apiBase)
            .build();
    }

    /**
     * Converts a major-unit BigDecimal amount (e.g. 12.34 EUR) to minor units (e.g. 1234)
     * as expected by Stripe's {@code unit_amount_decimal}.
     */
    private static BigDecimal toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal fromMinorUnits(Long minor) {
        return minor == null ? null
            : BigDecimal.valueOf(minor).movePointLeft(2);
    }

    // ==================== helpers: correlation id extraction ====================

    /**
     * Extracts the {@code correlation_id} from the event metadata. Pure JSON parsing —
     * the data is NOT trusted at this point (signature not yet verified).
     *
     * <p>Stripe places the metadata either on the checkout session
     * ({@code data.object.metadata.correlation_id}) or on the payment intent
     * ({@code data.object.metadata.correlation_id}). We check both paths.
     */
    String extractCorrelationId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataObject = root.path("data").path("object");
            if (dataObject.isMissingNode()) {
                return null;
            }
            JsonNode metadata = dataObject.path("metadata");
            if (!metadata.isMissingNode() && metadata.has(METADATA_KEY_CORRELATION_ID)) {
                String value = metadata.get(METADATA_KEY_CORRELATION_ID).asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            // Malformed JSON — treat as no correlation id; signature verification will fail.
            return null;
        }
    }

    /**
     * Extracts the Stripe checkout session id from the event payload
     * ({@code data.object.id}). Pure JSON parsing — untrusted at this point.
     */
    String extractSessionId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataObject = root.path("data").path("object");
            if (dataObject.isMissingNode()) {
                return null;
            }
            JsonNode id = dataObject.path("id");
            if (id.isMissingNode()) {
                return null;
            }
            String value = id.asText(null);
            return (value != null && !value.isBlank()) ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== helpers: webhook secret resolution ====================

    /**
     * Resolves the Stripe webhook secret to verify the signature with.
     *
     * <p>Try by correlation id first (from metadata), then by session id
     * ({@code data.object.id}). Both paths go directly from the {@code PaymentTransaction}
     * to its stored {@code productDefinitionId} → {@code stripe_webhook_secret}.
     *
     * <p>If neither lookup succeeds, returns {@code null} — the caller must reject the
     * webhook since no product can be identified to verify the signature against.
     */
    private String resolveWebhookSecret(String correlationId, String sessionId) {
        if (correlationId != null) {
            Optional<String> secret = findWebhookSecretByCorrelationId(correlationId);
            if (secret.isPresent()) {
                return secret.get();
            }
        }
        if (sessionId != null) {
            return findWebhookSecretBySessionId(sessionId).orElse(null);
        }
        return null;
    }

    private Optional<String> findWebhookSecretByCorrelationId(String correlationId) {
        // PaymentTransaction (by correlationId) → productDefinitionId → stripe_webhook_secret
        return em.createQuery(
                "SELECT pd.stripeWebhookSecret FROM NonMultitenancyProductDefinition pd " +
                "WHERE pd.id IN (" +
                "  SELECT t.productDefinitionId FROM PaymentTransaction t " +
                "  WHERE t.correlationId = :cid" +
                ")",
                String.class)
            .setParameter("cid", correlationId)
            .setMaxResults(1)
            .getResultStream()
            .filter(s -> s != null && !s.isBlank())
            .findFirst();
    }

    /**
     * Looks up the webhook secret via the payment transaction's PSP session id.
     * PaymentTransaction (by pspSessionId) → productDefinitionId → stripe_webhook_secret.
     */
    private Optional<String> findWebhookSecretBySessionId(String sessionId) {
        return em.createQuery(
                "SELECT pd.stripeWebhookSecret FROM NonMultitenancyProductDefinition pd " +
                "WHERE pd.id IN (" +
                "  SELECT t.productDefinitionId FROM PaymentTransaction t " +
                "  WHERE t.pspSessionId = :sid" +
                ")",
                String.class)
            .setParameter("sid", sessionId)
            .setMaxResults(1)
            .getResultStream()
            .filter(s -> s != null && !s.isBlank())
            .findFirst();
    }

    // ==================== helpers: signature verification ====================

    private Event verify(String payload, String signature, String secret) {
        try {
            return Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Webhook signature verification failed")
                    .build());
        }
    }

    // ==================== helpers: event mapping ====================

    private PaymentEventResult mapEvent(Event event, String rawPayload, String correlationId) {
        PaymentEventResult result = new PaymentEventResult();
        result.setPspEventId(event.getId());
        result.setEventType(event.getType());
        result.setCorrelationId(correlationId);
        result.setRawPayload(rawPayload);
        result.setMatched(correlationId != null);

        switch (event.getType()) {
            case "checkout.session.completed",
                 "checkout.session.async_payment_succeeded" -> {
                Session session = asObject(event, Session.class);
                if (session != null) {
                    // Re-read the correlation id from the verified session metadata in case
                    // the untrusted parse missed it (e.g. metadata on a different path).
                    result.setCorrelationId(metadataCorrelationId(session.getMetadata(), correlationId));
                    result.setMatched(result.getCorrelationId() != null);
                    result.setPspSessionId(session.getId());
                    result.setPspTransactionRef(session.getPaymentIntent());
                    result.setCurrency(session.getCurrency());
                    result.setGrossAmount(fromMinorUnits(session.getAmountTotal()));
                    boolean paid = "paid".equalsIgnoreCase(session.getPaymentStatus());
                    result.setStatus(paid ? PaymentStatus.SUCCEEDED : PaymentStatus.PENDING);
                }
            }
            case "checkout.session.async_payment_failed" -> {
                Session session = asObject(event, Session.class);
                if (session != null) {
                    result.setCorrelationId(metadataCorrelationId(session.getMetadata(), correlationId));
                    result.setMatched(result.getCorrelationId() != null);
                    result.setPspSessionId(session.getId());
                    result.setPspTransactionRef(session.getPaymentIntent());
                    result.setCurrency(session.getCurrency());
                    result.setGrossAmount(fromMinorUnits(session.getAmountTotal()));
                }
                result.setStatus(PaymentStatus.FAILED);
            }
            case "payment_intent.succeeded" -> {
                PaymentIntent intent = asObject(event, PaymentIntent.class);
                if (intent != null) {
                    result.setCorrelationId(metadataCorrelationId(intent.getMetadata(), correlationId));
                    result.setMatched(result.getCorrelationId() != null);
                    result.setPspTransactionRef(intent.getId());
                    result.setCurrency(intent.getCurrency());
                    result.setGrossAmount(fromMinorUnits(intent.getAmount()));
                    result.setStatus(PaymentStatus.SUCCEEDED);
                }
            }
            case "charge.updated" -> {
                Charge charge = asObject(event, Charge.class);
                if (charge != null) {
                    result.setCorrelationId(metadataCorrelationId(charge.getMetadata(), correlationId));
                    result.setMatched(result.getCorrelationId() != null);
                    result.setPspTransactionRef(charge.getPaymentIntent());
                    result.setCurrency(charge.getCurrency());
                    result.setGrossAmount(fromMinorUnits(charge.getAmount()));
                    if (charge.getBalanceTransactionObject() != null) {
                        result.setFeeAmount(fromMinorUnits(charge.getBalanceTransactionObject().getFee()));
                    }
                }
                // charge.updated only updates the fee; status stays PENDING so PaymentService
                // treats it as an IGNORED event (no state transition).
                result.setStatus(PaymentStatus.PENDING);
            }
            default -> {
                // Event type not actively processed; PaymentService records IGNORED.
                result.setStatus(PaymentStatus.PENDING);
            }
        }
        return result;
    }

    private static String metadataCorrelationId(
            java.util.Map<String, String> metadata, String fallback) {
        if (metadata != null) {
            String value = metadata.get(METADATA_KEY_CORRELATION_ID);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    /**
     * Deserialises the event's data object to the given Stripe model class, or returns
     * {@code null} if deserialisation fails (the event is then recorded as IGNORED).
     */
    private <T extends com.stripe.model.StripeObject> T asObject(Event event, Class<T> type) {
        try {
            com.stripe.model.StripeObject obj = event.getDataObjectDeserializer()
                .deserializeUnsafe();
            return type.isInstance(obj) ? type.cast(obj) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
