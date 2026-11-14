package dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto;

import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractAccountRole.RoleType;

import java.time.LocalDate;

/**
 * A single account's relationship to a contract, as recorded in
 * {@code T_contract_account_role}. The {@code accountId} is the account's
 * identifier (the caller's login name, typically an email address).
 */
public class ContractAccountRoleResponse {

    private String id;
    private String accountId;
    private RoleType roleType;
    private LocalDate validFrom;
    private LocalDate validUntil;

    public ContractAccountRoleResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}
