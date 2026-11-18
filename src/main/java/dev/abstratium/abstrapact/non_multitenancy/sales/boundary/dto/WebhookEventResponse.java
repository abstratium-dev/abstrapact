package dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;

import java.time.LocalDateTime;

/**
 * A single webhook call recorded for a payment transaction.
 *
 * <p>Exposes the audit fields of a {@link WebhookEvent} without leaking PSP
 * credentials. The {@code paymentTransactionId} and {@code organisationId}
 * fields are intentionally omitted: the response is always scoped to a payment
 * transaction that has already been verified to belong to the caller's
 * organisation.
 */
public class WebhookEventResponse {

    private String id;
    private String pspIdentifier;
    private String pspEventId;
    private String eventType;
    private String correlationId;
    private boolean matched;
    private WebhookEvent.ProcessingResult processingResult;
    private String rawPayload;
    private String rejectionReason;
    private LocalDateTime receivedAt;

    public WebhookEventResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPspIdentifier() {
        return pspIdentifier;
    }

    public void setPspIdentifier(String pspIdentifier) {
        this.pspIdentifier = pspIdentifier;
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

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public WebhookEvent.ProcessingResult getProcessingResult() {
        return processingResult;
    }

    public void setProcessingResult(WebhookEvent.ProcessingResult processingResult) {
        this.processingResult = processingResult;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
