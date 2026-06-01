package dev.abstratium.conditions.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_contract_terms_link")
@Audited
public class ContractTermsLink {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    @JsonIgnore
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_and_conditions_id", nullable = false)
    @JsonIgnore
    private TermsAndConditions termsAndConditions;

    @Column(name = "terms_version_at_signing", length = 50)
    private String termsVersionAtSigning;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 30, nullable = false)
    private TermsScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_line_item_id")
    @JsonIgnore
    private ContractLineItem contractLineItem;

    public ContractTermsLink() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public TermsAndConditions getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(TermsAndConditions termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public String getTermsVersionAtSigning() {
        return termsVersionAtSigning;
    }

    public void setTermsVersionAtSigning(String termsVersionAtSigning) {
        this.termsVersionAtSigning = termsVersionAtSigning;
    }

    public TermsScope getScope() {
        return scope;
    }

    public void setScope(TermsScope scope) {
        this.scope = scope;
    }

    public ContractLineItem getContractLineItem() {
        return contractLineItem;
    }

    public void setContractLineItem(ContractLineItem contractLineItem) {
        this.contractLineItem = contractLineItem;
    }

    public enum TermsScope {
        GENERAL,
        SPECIAL_FOR_LINE_ITEM
    }
}
