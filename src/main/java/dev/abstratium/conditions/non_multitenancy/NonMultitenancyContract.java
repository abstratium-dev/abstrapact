package dev.abstratium.conditions.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.abstratium.conditions.entity.ContractState;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_contract")
@Audited
public class NonMultitenancyContract {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "contract_reference", length = 100, nullable = false, unique = true)
    private String contractReference;

    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model", length = 20, nullable = false)
    private PaymentModel paymentModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 30, nullable = false)
    private ContractState state;

    @Column(name = "grand_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "public_notes", length = 1000)
    private String publicNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyContractLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyContractTermsLink> termsLinks = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancySignatory> signatories = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyContractAccountRole> accountRoles = new ArrayList<>();

    public NonMultitenancyContract() {
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

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getPublicNotes() {
        return publicNotes;
    }

    public void setPublicNotes(String publicNotes) {
        this.publicNotes = publicNotes;
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

    public List<NonMultitenancyContractLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<NonMultitenancyContractLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public List<NonMultitenancyContractTermsLink> getTermsLinks() {
        return termsLinks;
    }

    public void setTermsLinks(List<NonMultitenancyContractTermsLink> termsLinks) {
        this.termsLinks = termsLinks;
    }

    public List<NonMultitenancySignatory> getSignatories() {
        return signatories;
    }

    public void setSignatories(List<NonMultitenancySignatory> signatories) {
        this.signatories = signatories;
    }

    public List<NonMultitenancyContractAccountRole> getAccountRoles() {
        return accountRoles;
    }

    public void setAccountRoles(List<NonMultitenancyContractAccountRole> accountRoles) {
        this.accountRoles = accountRoles;
    }

    public enum PaymentModel {
        PREPAID,
        POSTPAID
    }
}
