package dev.abstratium.product.boundary.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AllowedValueResponse {
    private String id;
    private String allowedValue;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAllowedValue() {
        return allowedValue;
    }

    public void setAllowedValue(String allowedValue) {
        this.allowedValue = allowedValue;
    }
}
