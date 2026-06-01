package dev.abstratium.conditions.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

@Entity
@Table(name = "T_signatory")
@Audited
public class Signatory {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "signatory_type", length = 20, nullable = false)
    private SignatoryType signatoryType;

    @Column(name = "full_name", length = 255, nullable = false)
    private String fullName;

    @Column(name = "role_title", length = 100)
    private String roleTitle;

    @Column(name = "organisation_name", length = 255)
    private String organisationName;

    @Column(name = "signature_date")
    private LocalDate signatureDate;

    @Column(name = "signature_place", length = 100)
    private String signaturePlace;

    @Column(name = "digital_signature_reference", length = 255)
    private String digitalSignatureReference;

    public Signatory() {
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

    public SignatoryType getSignatoryType() {
        return signatoryType;
    }

    public void setSignatoryType(SignatoryType signatoryType) {
        this.signatoryType = signatoryType;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public LocalDate getSignatureDate() {
        return signatureDate;
    }

    public void setSignatureDate(LocalDate signatureDate) {
        this.signatureDate = signatureDate;
    }

    public String getSignaturePlace() {
        return signaturePlace;
    }

    public void setSignaturePlace(String signaturePlace) {
        this.signaturePlace = signaturePlace;
    }

    public String getDigitalSignatureReference() {
        return digitalSignatureReference;
    }

    public void setDigitalSignatureReference(String digitalSignatureReference) {
        this.digitalSignatureReference = digitalSignatureReference;
    }

    public enum SignatoryType {
        SME,
        CUSTOMER
    }
}
