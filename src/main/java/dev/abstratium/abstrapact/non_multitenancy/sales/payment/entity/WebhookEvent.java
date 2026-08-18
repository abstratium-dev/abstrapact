package dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Audit log of <strong>every</strong> webhook call that passes signature verification —
 * matched, unmatched, stale, and duplicate events alike — providing a complete audit trail
 * of all PSP communication.
 *
 * <p>Unverified events (signature failure) are <em>not</em> recorded: they cannot be trusted.
 *
 * <p>See {@code docs/DESIGN_OF_PAYMENT.md}.
 */
@Entity
@Table(name = "T_webhook_event")
@Audited
public class WebhookEvent {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36)
    private String organisationId;

    @Column(name = "psp_identifier", length = 30, nullable = false)
    private String pspIdentifier;

    @Column(name = "psp_event_id", length = 255, nullable = false)
    private String pspEventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "payment_transaction_id", length = 36)
    private String paymentTransactionId;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_result", length = 30, nullable = false)
    private ProcessingResult processingResult;

    @Lob
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    public WebhookEvent() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
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

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public ProcessingResult getProcessingResult() {
        return processingResult;
    }

    public void setProcessingResult(ProcessingResult processingResult) {
        this.processingResult = processingResult;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    /**
     * Outcome of processing a verified webhook event.
     *
     * <ul>
     *   <li>{@code PROCESSED} — matching transaction found, updated, and contract transitioned.</li>
     *   <li>{@code DUPLICATE} — event already processed (transaction in terminal state). No state change.</li>
     *   <li>{@code UNMATCHED} — no matching PaymentTransaction found. No state change.</li>
     *   <li>{@code STALE} — matching transaction found but too old. Transaction marked STALE,
     *       contract not transitioned. Requires manual review.</li>
     *   <li>{@code IGNORED} — event type not actively processed (e.g. {@code charge.refunded}
     *       before refund support). No state change.</li>
     * </ul>
     */
    public enum ProcessingResult {
        PROCESSED,
        DUPLICATE,
        UNMATCHED,
        STALE,
        IGNORED
    }
}
