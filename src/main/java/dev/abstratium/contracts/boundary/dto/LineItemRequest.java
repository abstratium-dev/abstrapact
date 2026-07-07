package dev.abstratium.contracts.boundary.dto;

import java.util.List;

public class LineItemRequest {

    private String productDefinitionId;
    private Integer displayOrder;
    private List<PartInstanceAttributeRequest> attributes;

    public String getProductDefinitionId() {
        return productDefinitionId;
    }

    public void setProductDefinitionId(String productDefinitionId) {
        this.productDefinitionId = productDefinitionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<PartInstanceAttributeRequest> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<PartInstanceAttributeRequest> attributes) {
        this.attributes = attributes;
    }
}
