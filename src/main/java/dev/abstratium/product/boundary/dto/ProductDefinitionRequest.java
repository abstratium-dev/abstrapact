package dev.abstratium.product.boundary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating or updating a complete product definition
 * including parts, attributes, and allowed values in a single request.
 */
@RegisterForReflection
public class ProductDefinitionRequest {
    public ProductDefinitionRequest() {
    }

    private String id;
    private String productCode;
    private String description;
    private ProductDefinition.BillingModel billingModel;
    private LocalDate productValidFrom;
    private LocalDate productValidUntil;
    private String termsAndConditionsCode;
    private List<PartRequest> parts;

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

    public List<PartRequest> getParts() {
        return parts;
    }

    public void setParts(List<PartRequest> parts) {
        this.parts = parts;
    }
}
