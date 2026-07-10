package dev.abstratium.conditions.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_contract")
@Audited
public class Contract {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "contract_reference", length = 50, nullable = false, unique = true)
    private String contractReference;

    @Column(name = "contract_date")
    private LocalDate contractDate;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "grand_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "billing_address_line1", length = 255)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 255)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_postcode", length = 20)
    private String billingPostcode;

    @Column(name = "billing_country", length = 2)
    private String billingCountry;

    @Column(name = "delivery_address_line1", length = 255)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2", length = 255)
    private String deliveryAddressLine2;

    @Column(name = "delivery_city", length = 100)
    private String deliveryCity;

    @Column(name = "delivery_postcode", length = 20)
    private String deliveryPostcode;

    @Column(name = "delivery_country", length = 2)
    private String deliveryCountry;

    @Column(name = "payment_terms", length = 50)
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model", length = 20, nullable = false)
    private PaymentModel paymentModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private ContractState state;

    @Column(name = "public_notes", columnDefinition = "TEXT")
    private String publicNotes;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<ContractLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<ContractTermsLink> termsLinks = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<Signatory> signatories = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<ContractAccountRole> accountRoles = new ArrayList<>();

    public Contract() {
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

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public LocalDate getContractDate() {
        return contractDate;
    }

    public void setContractDate(LocalDate contractDate) {
        this.contractDate = contractDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getBillingAddressLine1() {
        return billingAddressLine1;
    }

    public void setBillingAddressLine1(String billingAddressLine1) {
        this.billingAddressLine1 = billingAddressLine1;
    }

    public String getBillingAddressLine2() {
        return billingAddressLine2;
    }

    public void setBillingAddressLine2(String billingAddressLine2) {
        this.billingAddressLine2 = billingAddressLine2;
    }

    public String getBillingCity() {
        return billingCity;
    }

    public void setBillingCity(String billingCity) {
        this.billingCity = billingCity;
    }

    public String getBillingPostcode() {
        return billingPostcode;
    }

    public void setBillingPostcode(String billingPostcode) {
        this.billingPostcode = billingPostcode;
    }

    public String getBillingCountry() {
        return billingCountry;
    }

    public void setBillingCountry(String billingCountry) {
        this.billingCountry = billingCountry;
    }

    public String getDeliveryAddressLine1() {
        return deliveryAddressLine1;
    }

    public void setDeliveryAddressLine1(String deliveryAddressLine1) {
        this.deliveryAddressLine1 = deliveryAddressLine1;
    }

    public String getDeliveryAddressLine2() {
        return deliveryAddressLine2;
    }

    public void setDeliveryAddressLine2(String deliveryAddressLine2) {
        this.deliveryAddressLine2 = deliveryAddressLine2;
    }

    public String getDeliveryCity() {
        return deliveryCity;
    }

    public void setDeliveryCity(String deliveryCity) {
        this.deliveryCity = deliveryCity;
    }

    public String getDeliveryPostcode() {
        return deliveryPostcode;
    }

    public void setDeliveryPostcode(String deliveryPostcode) {
        this.deliveryPostcode = deliveryPostcode;
    }

    public String getDeliveryCountry() {
        return deliveryCountry;
    }

    public void setDeliveryCountry(String deliveryCountry) {
        this.deliveryCountry = deliveryCountry;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public PaymentModel getPaymentModel() {
        return paymentModel;
    }

    public void setPaymentModel(PaymentModel paymentModel) {
        this.paymentModel = paymentModel;
    }

    public ContractState getState() {
        return state;
    }

    public void setState(ContractState state) {
        this.state = state;
    }

    public String getPublicNotes() {
        return publicNotes;
    }

    public void setPublicNotes(String publicNotes) {
        this.publicNotes = publicNotes;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public void setInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
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

    public List<ContractLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<ContractLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public List<ContractTermsLink> getTermsLinks() {
        return termsLinks;
    }

    public void setTermsLinks(List<ContractTermsLink> termsLinks) {
        this.termsLinks = termsLinks;
    }

    public List<Signatory> getSignatories() {
        return signatories;
    }

    public void setSignatories(List<Signatory> signatories) {
        this.signatories = signatories;
    }

    public List<ContractAccountRole> getAccountRoles() {
        return accountRoles;
    }

    public void setAccountRoles(List<ContractAccountRole> accountRoles) {
        this.accountRoles = accountRoles;
    }

    public enum PaymentModel {
        PREPAID,
        POSTPAID
    }
}
