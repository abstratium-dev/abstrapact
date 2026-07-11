package dev.abstratium.conditions.non_multitenancy;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;

@Entity
@Table(name = "T_contract_account_role")
@Audited
public class NonMultitenancyContractAccountRole {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private NonMultitenancyContract contract;

    @Column(name = "account_id", length = 255, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 30, nullable = false)
    private RoleType roleType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    public NonMultitenancyContractAccountRole() {
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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public enum RoleType {
        CUSTOMER
    }
}
