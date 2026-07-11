package dev.abstratium.non_multitenancy.contract.boundary.dto;

import dev.abstratium.conditions.entity.ContractState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CustomerContractSummary {

    private String id;
    private String contractReference;
    private String sellerOrganisationId;
    private LocalDate contractDate;
    private String currency;
    private BigDecimal grandTotal;
    private ContractState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CustomerContractSummary() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public String getSellerOrganisationId() {
        return sellerOrganisationId;
    }

    public void setSellerOrganisationId(String sellerOrganisationId) {
        this.sellerOrganisationId = sellerOrganisationId;
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

    public ContractState getState() {
        return state;
    }

    public void setState(ContractState state) {
        this.state = state;
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
