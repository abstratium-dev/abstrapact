package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;

import java.math.BigDecimal;

/**
 * Result of parsing and verifying a webhook event through the active {@code PSPInterface}.
 *
 * <p>{@code PaymentService.handlePaymentResult} consumes this to update the
 * {@link PaymentTransaction} and transition the contract.
 */
public class PaymentEventResult {

    /** PSP event id (e.g. Stripe's {@code evt_...}), used for deduplication. */
    private String pspEventId;

    /** Event type, e.g. {@code "payment_intent.succeeded"}. */
    private String eventType;

    /** Matches the correlation id stored at creation; may be {@code null} for unmatched events. */
    private String correlationId;

    /** PSP transaction reference, e.g. Stripe's {@code pi_...}. */
    private String pspTransactionRef;

    /** PSP session/checkout id (e.g. Stripe's {@code cs_...}), when available on the event. */
    private String pspSessionId;

    private BigDecimal grossAmount;

    /** May be {@code null} if not yet available (fees may arrive in a later event). */
    private BigDecimal feeAmount;

    private String currency;

    /** {@code SUCCEEDED}, {@code FAILED}, or {@code PENDING} (for events that don't change state). */
    private PaymentTransaction.PaymentStatus status;

    /** {@code true} if a {@code PaymentTransaction} was found for the correlation id. */
    private boolean matched;

    /** The original webhook payload, stored for every event (audit trail). */
    private String rawPayload;

    public PaymentEventResult() {
    }

    public String getPspEventId() {
        return pspEventId;
    }

    public void setPspEventId(String pspEventId) {
        this.pspEventId = pspEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPspTransactionRef() {
        return pspTransactionRef;
    }

    public void setPspTransactionRef(String pspTransactionRef) {
        this.pspTransactionRef = pspTransactionRef;
    }

    public String getPspSessionId() {
        return pspSessionId;
    }

    public void setPspSessionId(String pspSessionId) {
        this.pspSessionId = pspSessionId;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentTransaction.PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentTransaction.PaymentStatus status) {
        this.status = status;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
