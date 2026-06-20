package dev.abstratium.conditions.boundary.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TermsAndConditionsCodeSummary {
    private String code;
    private String title;

    public TermsAndConditionsCodeSummary() {
    }

    public TermsAndConditionsCodeSummary(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
