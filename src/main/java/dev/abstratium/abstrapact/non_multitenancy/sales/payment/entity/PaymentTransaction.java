package dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Internal record of a single payment attempt through a Payment Service Provider.
 *
 * <p>A contract may have multiple transactions (e.g. a failed attempt followed by a
 * successful one). The contract id is stored as a plain column (no JPA relationship),
 * mirroring the {@code NonMultitenancyProcessInstance} pattern to keep the payment data
 * lifecycle independent from the contract data lifecycle.
 *
 * <p>See {@code docs/DESIGN_OF_PAYMENT.md}.
 */
@Entity
@Table(name = "T_payment_transaction")
@Audited
public class PaymentTransaction {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "contract_id", length = 36, nullable = false)
    private String contractId;

    /**
     * The product definition that provides the Stripe credentials for this transaction.
     * Stored directly to avoid joining through contract line items when resolving the
     * product definition for webhook signature verification and redirect URLs.
     */
    @Column(name = "product_definition_id", length = 36, nullable = false)
    private String productDefinitionId;

    @Column(name = "psp_identifier", length = 30, nullable = false)
    private String pspIdentifier;

    @Column(name = "correlation_id", length = 36, nullable = false, unique = true)
    private String correlationId;

    @Column(name = "psp_session_id", length = 255)
    private String pspSessionId;

    @Column(name = "psp_transaction_ref", length = 255)
    private String pspTransactionRef;

    @Column(name = "gross_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentTransaction() {
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

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public String getProductDefinitionId() {
        return productDefinitionId;
    }

    public void setProductDefinitionId(String productDefinitionId) {
        this.productDefinitionId = productDefinitionId;
    }

    public String getPspIdentifier() {
        return pspIdentifier;
    }

    public void setPspIdentifier(String pspIdentifier) {
        this.pspIdentifier = pspIdentifier;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPspSessionId() {
        return pspSessionId;
    }

    public void setPspSessionId(String pspSessionId) {
        this.pspSessionId = pspSessionId;
    }

    public String getPspTransactionRef() {
        return pspTransactionRef;
    }

    public void setPspTransactionRef(String pspTransactionRef) {
        this.pspTransactionRef = pspTransactionRef;
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

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Lifecycle of a payment transaction.
     *
     * <ul>
     *   <li>{@code PENDING} — payment created, awaiting PSP confirmation.</li>
     *   <li>{@code SUCCEEDED} — PSP confirmed the payment; contract transitioned to RUNNING.</li>
     *   <li>{@code FAILED} — PSP reported failure; contract remains AWAITING_PAYMENT.</li>
     *   <li>{@code STALE} — success arrived too late (see staleness check); requires manual review.</li>
     * </ul>
     *
     * {@code SUCCEEDED}, {@code FAILED} and {@code STALE} are terminal — subsequent events
     * for the same correlation id are recorded as {@code DUPLICATE}.
     */
    public enum PaymentStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        STALE
    }
}
