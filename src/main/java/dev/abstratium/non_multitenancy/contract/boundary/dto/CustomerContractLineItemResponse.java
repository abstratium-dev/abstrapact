package dev.abstratium.non_multitenancy.contract.boundary.dto;

import dev.abstratium.product.non_multitenancy.NonMultitenancyProductInstance;

import java.math.BigDecimal;

public class CustomerContractLineItemResponse {

    private String id;
    private Integer displayOrder;
    private BigDecimal lineTotal;
    private NonMultitenancyProductInstance productInstance;

    public CustomerContractLineItemResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public NonMultitenancyProductInstance getProductInstance() {
        return productInstance;
    }

    public void setProductInstance(NonMultitenancyProductInstance productInstance) {
        this.productInstance = productInstance;
    }
}
