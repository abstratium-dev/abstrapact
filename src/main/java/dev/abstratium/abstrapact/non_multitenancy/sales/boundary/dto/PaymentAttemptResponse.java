package dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single payment attempt recorded for a contract.
 *
 * <p>Exposes the lifecycle status and financial details of a
 * {@link PaymentTransaction} without leaking PSP credentials.
 */
public class PaymentAttemptResponse {

    private String id;
    private PaymentTransaction.PaymentStatus status;
    private BigDecimal grossAmount;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private String currency;
    private String pspIdentifier;
    private String pspSessionId;
    private String pspTransactionRef;
    private String checkoutUrl;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PaymentAttemptResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PaymentTransaction.PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentTransaction.PaymentStatus status) {
        this.status = status;
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

    public String getPspIdentifier() {
        return pspIdentifier;
    }

    public void setPspIdentifier(String pspIdentifier) {
        this.pspIdentifier = pspIdentifier;
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

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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
}
