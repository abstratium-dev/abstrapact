package dev.abstratium.non_multitenancy.contract.boundary.dto;

public class PartInstanceAttributeRequest {

    private String attributeName;
    private String attributeValue;

    public PartInstanceAttributeRequest() {
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }
}
