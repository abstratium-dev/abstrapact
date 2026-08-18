package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto;

import java.math.BigDecimal;

/**
 * Request to create a payment through the active {@code PSPInterface}.
 *
 * <p>PSP credentials and redirect URLs are resolved from the product definition by
 * {@code PaymentService} and passed in here so the PSP implementation stays agnostic
 * of the product model.
 */
public class CreatePaymentRequest {

    /** Internal contract id. */
    private String contractId;

    /** Opaque UUID, stored as PSP metadata for webhook correlation. */
    private String correlationId;

    /** Gross amount to charge. */
    private BigDecimal amount;

    /** ISO 4217 currency code, e.g. {@code "EUR"}, {@code "CHF"}. */
    private String currency;

    /** Description shown on the PSP checkout page. */
    private String description;

    /** abstrapact's own success redirect endpoint (with {@code {CHECKOUT_SESSION_ID}} placeholder). */
    private String successUrl;

    /** abstrapact's own cancel redirect endpoint (with {@code {CHECKOUT_SESSION_ID}} placeholder). */
    private String cancelUrl;

    /** Stripe secret API key for this product, from the product definition. */
    private String stripeSecretKey;

    public CreatePaymentRequest() {
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getStripeSecretKey() {
        return stripeSecretKey;
    }

    public void setStripeSecretKey(String stripeSecretKey) {
        this.stripeSecretKey = stripeSecretKey;
    }
}
