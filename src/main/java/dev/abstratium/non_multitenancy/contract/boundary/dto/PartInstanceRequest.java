package dev.abstratium.non_multitenancy.contract.boundary.dto;

import java.util.ArrayList;
import java.util.List;

public class PartInstanceRequest {

    private String partCode;
    private List<PartInstanceAttributeRequest> attributeValues = new ArrayList<>();
    private List<PartInstanceRequest> childPartInstances = new ArrayList<>();

    public PartInstanceRequest() {
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public List<PartInstanceAttributeRequest> getAttributeValues() {
        return attributeValues;
    }

    public void setAttributeValues(List<PartInstanceAttributeRequest> attributeValues) {
        this.attributeValues = attributeValues;
    }

    public List<PartInstanceRequest> getChildPartInstances() {
        return childPartInstances;
    }

    public void setChildPartInstances(List<PartInstanceRequest> childPartInstances) {
        this.childPartInstances = childPartInstances;
    }
}
