package dev.abstratium.product.boundary.dto;

import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.List;

@RegisterForReflection
public class CompleteProductResponse {
    private String id;
    private String productCode;
    private String description;
    private ProductDefinition.BillingModel billingModel;
    private ProductDefinition.PaymentModel paymentModel;
    private LocalDate productValidFrom;
    private LocalDate productValidUntil;
    private String termsAndConditionsCode;
    private List<PartResponse> parts;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public ProductDefinition.BillingModel getBillingModel() {
        return billingModel;
    }

    public void setBillingModel(ProductDefinition.BillingModel billingModel) {
        this.billingModel = billingModel;
    }

    public ProductDefinition.PaymentModel getPaymentModel() {
        return paymentModel;
    }

    public void setPaymentModel(ProductDefinition.PaymentModel paymentModel) {
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

    public List<PartResponse> getParts() {
        return parts;
    }

    public void setParts(List<PartResponse> parts) {
        this.parts = parts;
    }
}
