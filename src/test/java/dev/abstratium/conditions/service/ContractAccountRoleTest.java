package dev.abstratium.conditions.service;

import dev.abstratium.conditions.entity.Contract;
import dev.abstratium.conditions.entity.ContractAccountRole;
import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContractAccountRoleTest {

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private Contract createContract() {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setContractReference("REF-ROLE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setGrandTotal(BigDecimal.ZERO);
        contract.setPaymentModel(Contract.PaymentModel.PREPAID);
        contract.setState(ContractState.DRAFT);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);
        return contract;
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void canPersistContractAccountRoleWithCustomerRole() {
        Contract contract = createContract();

        ContractAccountRole role = new ContractAccountRole();
        role.setId(UUID.randomUUID().toString());
        role.setContract(contract);
        role.setAccountId("user-sub-abc-123");
        role.setRoleType(ContractAccountRole.RoleType.CUSTOMER);
        role.setValidFrom(LocalDate.now());
        em.persist(role);
        em.flush();

        ContractAccountRole found = em.find(ContractAccountRole.class, role.getId());
        assertNotNull(found);
        assertEquals("user-sub-abc-123", found.getAccountId());
        assertEquals(ContractAccountRole.RoleType.CUSTOMER, found.getRoleType());
        assertEquals(contract.getId(), found.getContract().getId());
        assertNotNull(found.getOrganisationId());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void canQueryContractAccountRoleByAccountId() {
        String accountId = "user-query-" + System.currentTimeMillis();
        Contract contract = createContract();

        ContractAccountRole role = new ContractAccountRole();
        role.setId(UUID.randomUUID().toString());
        role.setContract(contract);
        role.setAccountId(accountId);
        role.setRoleType(ContractAccountRole.RoleType.CUSTOMER);
        em.persist(role);
        em.flush();

        List<ContractAccountRole> results = em.createQuery(
                "SELECT r FROM ContractAccountRole r WHERE r.accountId = :accountId",
                ContractAccountRole.class)
            .setParameter("accountId", accountId)
            .getResultList();

        assertEquals(1, results.size());
        assertEquals(accountId, results.get(0).getAccountId());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void contractAccountRoleValidUntilIsOptional() {
        Contract contract = createContract();

        ContractAccountRole role = new ContractAccountRole();
        role.setId(UUID.randomUUID().toString());
        role.setContract(contract);
        role.setAccountId("user-no-expiry-" + System.currentTimeMillis());
        role.setRoleType(ContractAccountRole.RoleType.CUSTOMER);
        em.persist(role);
        em.flush();

        ContractAccountRole found = em.find(ContractAccountRole.class, role.getId());
        assertNotNull(found);
        assertNull(found.getValidFrom());
        assertNull(found.getValidUntil());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void oneContractCanHaveMultipleAccountRoles() {
        Contract contract = createContract();

        for (int i = 0; i < 3; i++) {
            ContractAccountRole role = new ContractAccountRole();
            role.setId(UUID.randomUUID().toString());
            role.setContract(contract);
            role.setAccountId("user-multi-" + i);
            role.setRoleType(ContractAccountRole.RoleType.CUSTOMER);
            em.persist(role);
        }
        em.flush();

        List<ContractAccountRole> results = em.createQuery(
                "SELECT r FROM ContractAccountRole r WHERE r.contract.id = :contractId",
                ContractAccountRole.class)
            .setParameter("contractId", contract.getId())
            .getResultList();

        assertEquals(3, results.size());
    }
}
