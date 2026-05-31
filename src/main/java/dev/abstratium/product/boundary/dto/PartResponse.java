package dev.abstratium.product.boundary.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public class PartResponse {
    private String id;
    private String partCode;
    private String description;
    private BigDecimal unitPrice;
    private Integer displayOrder;
    private Integer minCardinality;
    private Integer maxCardinality;
    private List<PartResponse> childParts;
    private List<PartAttributeResponse> attributes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getMinCardinality() {
        return minCardinality;
    }

    public void setMinCardinality(Integer minCardinality) {
        this.minCardinality = minCardinality;
    }

    public Integer getMaxCardinality() {
        return maxCardinality;
    }

    public void setMaxCardinality(Integer maxCardinality) {
        this.maxCardinality = maxCardinality;
    }

    public List<PartResponse> getChildParts() {
        return childParts;
    }

    public void setChildParts(List<PartResponse> childParts) {
        this.childParts = childParts;
    }

    public List<PartAttributeResponse> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<PartAttributeResponse> attributes) {
        this.attributes = attributes;
    }
}
