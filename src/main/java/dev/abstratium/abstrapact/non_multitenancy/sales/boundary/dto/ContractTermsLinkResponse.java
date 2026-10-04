package dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto;

import dev.abstratium.abstrapact.contracts.entity.ContractTermsLink.TermsScope;

public class ContractTermsLinkResponse {

    private String id;
    private String termsCode;
    private String termsTitle;
    private String termsVersion;
    private TermsScope scope;

    public ContractTermsLinkResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTermsCode() {
        return termsCode;
    }

    public void setTermsCode(String termsCode) {
        this.termsCode = termsCode;
    }

    public String getTermsTitle() {
        return termsTitle;
    }

    public void setTermsTitle(String termsTitle) {
        this.termsTitle = termsTitle;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public TermsScope getScope() {
        return scope;
    }

    public void setScope(TermsScope scope) {
        this.scope = scope;
    }
}
