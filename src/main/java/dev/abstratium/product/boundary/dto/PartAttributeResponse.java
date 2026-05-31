package dev.abstratium.product.boundary.dto;

import dev.abstratium.product.entity.PartAttributeDefinition;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class PartAttributeResponse {
    private String id;
    private String attributeName;
    private PartAttributeDefinition.DataType dataType;
    private Boolean isRequired;
    private String defaultValue;
    private List<AllowedValueResponse> allowedValues;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public PartAttributeDefinition.DataType getDataType() {
        return dataType;
    }

    public void setDataType(PartAttributeDefinition.DataType dataType) {
        this.dataType = dataType;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<AllowedValueResponse> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<AllowedValueResponse> allowedValues) {
        this.allowedValues = allowedValues;
    }
}
