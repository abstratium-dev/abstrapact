package dev.abstratium.product.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

@Entity
@Table(name = "T_product_definition")
@Audited
public class ProductDefinition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "product_code", length = 50, nullable = false, unique = true)
    private String productCode;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_model", length = 20, nullable = false)
    private BillingModel billingModel;

    @Column(name = "product_valid_from")
    private LocalDate productValidFrom;

    @Column(name = "product_valid_until")
    private LocalDate productValidUntil;

    @Column(name = "terms_and_conditions_code", length = 50)
    private String termsAndConditionsCode;

    public ProductDefinition() {
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

    public enum BillingModel {
        FIXED_PRICE,
        SUBSCRIPTION
    }
}
