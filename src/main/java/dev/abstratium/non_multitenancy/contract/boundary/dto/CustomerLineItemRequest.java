package dev.abstratium.non_multitenancy.contract.boundary.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerLineItemRequest {

    private String productCode;
    private Integer displayOrder;
    private List<PartInstanceRequest> partInstances = new ArrayList<>();

    public CustomerLineItemRequest() {
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<PartInstanceRequest> getPartInstances() {
        return partInstances;
    }

    public void setPartInstances(List<PartInstanceRequest> partInstances) {
        this.partInstances = partInstances;
    }
}
