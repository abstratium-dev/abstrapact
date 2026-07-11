package dev.abstratium.conditions.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.abstratium.conditions.entity.ContractTermsLink.TermsScope;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_contract_terms_link")
@Audited
public class NonMultitenancyContractTermsLink {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_and_conditions_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyTermsAndConditions termsAndConditions;

    @Column(name = "terms_version_at_signing", length = 50)
    private String termsVersionAtSigning;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 30, nullable = false)
    private TermsScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_line_item_id")
    @JsonIgnore
    private NonMultitenancyContractLineItem contractLineItem;

    public NonMultitenancyContractTermsLink() {
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

    public NonMultitenancyContract getContract() {
        return contract;
    }

    public void setContract(NonMultitenancyContract contract) {
        this.contract = contract;
    }

    public NonMultitenancyTermsAndConditions getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(NonMultitenancyTermsAndConditions termsAndConditions) {
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

    public NonMultitenancyContractLineItem getContractLineItem() {
        return contractLineItem;
    }

    public void setContractLineItem(NonMultitenancyContractLineItem contractLineItem) {
        this.contractLineItem = contractLineItem;
    }
}
