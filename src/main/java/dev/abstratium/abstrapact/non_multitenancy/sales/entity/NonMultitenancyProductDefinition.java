package dev.abstratium.abstrapact.non_multitenancy.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_product_definition")
@Audited
public class NonMultitenancyProductDefinition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "product_code", length = 100, nullable = false, unique = true)
    private String productCode;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_model", length = 20, nullable = false)
    private BillingModel billingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model", length = 20)
    private PaymentModel paymentModel;

    @Column(name = "product_valid_from")
    private LocalDate productValidFrom;

    @Column(name = "product_valid_until")
    private LocalDate productValidUntil;

    @Column(name = "terms_and_conditions_code", length = 50)
    private String termsAndConditionsCode;

    @Column(name = "cross_tenant_api_allowed", nullable = false)
    private boolean crossTenantApiAllowed = false;

    // ---- Payment handling: per-product Stripe credentials and B2C redirect URLs ----
    // See docs/DESIGN_OF_PAYMENT.md. Credentials are @JsonIgnore so they are never
    // exposed in REST responses to customers.
    @JsonIgnore
    @Column(name = "stripe_secret_key", length = 100)
    private String stripeSecretKey;

    @JsonIgnore
    @Column(name = "stripe_webhook_secret", length = 100)
    private String stripeWebhookSecret;

    @Column(name = "payment_success_redirect_url", length = 500)
    private String paymentSuccessRedirectUrl;

    @Column(name = "payment_cancel_redirect_url", length = 500)
    private String paymentCancelRedirectUrl;

    @OneToMany(mappedBy = "productDefinition", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartDefinition> parts = new ArrayList<>();

    public NonMultitenancyProductDefinition() {
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

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BillingModel getBillingModel() {
        return billingModel;
    }

    public void setBillingModel(BillingModel billingModel) {
        this.billingModel = billingModel;
    }

    public PaymentModel getPaymentModel() {
        return paymentModel;
    }

    public void setPaymentModel(PaymentModel paymentModel) {
        this.paymentModel = paymentModel;
    }

    public LocalDate getProductValidFrom() {
        return productValidFrom;
    }

    public void setProductValidFrom(LocalDate productValidFrom) {
        this.productValidFrom = productValidFrom;
    }

    public LocalDate getProductValidUntil() {
        return productValidUntil;
    }

    public void setProductValidUntil(LocalDate productValidUntil) {
        this.productValidUntil = productValidUntil;
    }

    public String getTermsAndConditionsCode() {
        return termsAndConditionsCode;
    }

    public void setTermsAndConditionsCode(String termsAndConditionsCode) {
        this.termsAndConditionsCode = termsAndConditionsCode;
    }

    public boolean isCrossTenantApiAllowed() {
        return crossTenantApiAllowed;
    }

    public void setCrossTenantApiAllowed(boolean crossTenantApiAllowed) {
        this.crossTenantApiAllowed = crossTenantApiAllowed;
    }

    public String getStripeSecretKey() {
        return stripeSecretKey;
    }

    public void setStripeSecretKey(String stripeSecretKey) {
        this.stripeSecretKey = stripeSecretKey;
    }

    public String getStripeWebhookSecret() {
        return stripeWebhookSecret;
    }

    public void setStripeWebhookSecret(String stripeWebhookSecret) {
        this.stripeWebhookSecret = stripeWebhookSecret;
    }

    public String getPaymentSuccessRedirectUrl() {
        return paymentSuccessRedirectUrl;
    }

    public void setPaymentSuccessRedirectUrl(String paymentSuccessRedirectUrl) {
        this.paymentSuccessRedirectUrl = paymentSuccessRedirectUrl;
    }

    public String getPaymentCancelRedirectUrl() {
        return paymentCancelRedirectUrl;
    }

    public void setPaymentCancelRedirectUrl(String paymentCancelRedirectUrl) {
        this.paymentCancelRedirectUrl = paymentCancelRedirectUrl;
    }

    public List<NonMultitenancyPartDefinition> getParts() {
        return parts;
    }

    public void setParts(List<NonMultitenancyPartDefinition> parts) {
        this.parts = parts;
    }

    public enum BillingModel {
        FIXED_PRICE,
        SUBSCRIPTION,
        USAGE_BASED
    }

    public enum PaymentModel {
        PREPAID,
        POSTPAID
    }
}
