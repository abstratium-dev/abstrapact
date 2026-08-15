package dev.abstratium.abstrapact.non_multitenancy.sales.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerContractResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.service.ProductDefinitionService;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SalesProcessServiceTest {

    @Inject
    SalesProcessService salesProcessService;

    @Inject
    NonMultitenancyCustomerContractService contractService;

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction tx;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private static final String ACCOUNT_ID = "sales-process-test-user";

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() {
        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("SP-PROD-001");
        pd.setDescription("Sales Process Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(pd);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private CustomerContractResponse createDraftContract() {
        currentOrgContext.setOrgId(defaultOrgId);
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode(OrgScopedCodec.encode(defaultOrgId, "SP-PROD-001", "Product"));
        li.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference("SP-TEST-" + UUID.randomUUID());
        req.setLineItems(List.of(li));
        return contractService.createContract(req, defaultOrgId, ACCOUNT_ID);
    }

    private NonMultitenancyProcessInstance loadProcess(String contractId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyProcessInstance p WHERE p.contractId = :contractId",
                NonMultitenancyProcessInstance.class)
            .setParameter("contractId", contractId)
            .getSingleResult();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void startSalesProcessCreatesProcessInstanceInProgress() {
        CustomerContractResponse response = createDraftContract();

        NonMultitenancyProcessInstance process = loadProcess(response.getId());

        assertEquals(ProcessInstanceState.IN_PROGRESS, process.getState());
        assertEquals(defaultOrgId, process.getOrganisationId());
        assertEquals("sales-process", process.getProcessName());
        assertEquals(response.getId(), process.getContractId());
        assertEquals("1.0", process.getProcessVersion());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void startSalesProcessRecordsInitialStep() {
        CustomerContractResponse response = createDraftContract();

        NonMultitenancyProcessInstance process = loadProcess(response.getId());

        assertFalse(process.getSteps().isEmpty());
        assertEquals(ContractState.DRAFT.name(), process.getSteps().get(0).getToState());
        assertEquals(ACCOUNT_ID, process.getSteps().get(0).getActorUserId());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractTransitionsDraftToOffered() {
        CustomerContractResponse response = createDraftContract();

        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.OFFERED, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractRecordsStep() {
        CustomerContractResponse response = createDraftContract();

        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long offerSteps = process.getSteps().stream()
            .filter(s -> ContractState.OFFERED.name().equals(s.getToState()))
            .count();
        assertEquals(1, offerSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsWhenNotDraft() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractTransitionsOfferedToApprovedViaAutoApproval() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.APPROVED, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractRecordsAcceptanceAndApprovalSteps() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long acceptSteps = process.getSteps().stream()
            .filter(s -> ContractState.ACCEPTED.name().equals(s.getToState()))
            .count();
        assertEquals(1, acceptSteps);
        long approvedSteps = process.getSteps().stream()
            .filter(s -> ContractState.APPROVED.name().equals(s.getToState()))
            .count();
        assertEquals(1, approvedSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsWhenNotOffered() {
        CustomerContractResponse response = createDraftContract();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // ==================== Approval (placeholder) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractTransitionsAcceptedToApproved() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        // Move to ACCEPTED without auto-approval by manually setting the state.
        setContractState(response.getId(), ContractState.ACCEPTED);

        salesProcessService.approveContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.APPROVED, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractRecordsStep() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.ACCEPTED);

        salesProcessService.approveContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long approvedSteps = process.getSteps().stream()
            .filter(s -> ContractState.APPROVED.name().equals(s.getToState()))
            .count();
        assertEquals(1, approvedSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsWhenNotAccepted() {
        CustomerContractResponse response = createDraftContract();
        // Contract is still DRAFT.
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ==================== Payment handling (placeholder) ====================

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingLeavesContractInApproved() {
        // The payment handling placeholder must not change the contract state.
        // Once payment specs are provided, this test will be updated to verify
        // the actual payment state transitions.
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.APPROVED);

        salesProcessService.triggerPaymentHandling(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.APPROVED, contract.getState());
    }

    // ==================== Helper ====================

    /**
     * Directly sets the contract state via JPA, bypassing the sales process.
     * Used to set up intermediate states (ACCEPTED, APPROVED) for testing
     * individual transition methods in isolation.
     */
    private void setContractState(String contractId, ContractState state) {
        try {
            tx.begin();
            NonMultitenancyContract c = em.find(NonMultitenancyContract.class, contractId);
            c.setState(state);
            em.merge(c);
            tx.commit();
        } catch (Exception e) {
            try { tx.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsForUnknownContract() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract("does-not-exist", ACCOUNT_ID));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ==================== Account-link authorisation ====================

    private static final String OTHER_ACCOUNT_ID = "intruder-account";

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void offerContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.offerContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void approveContractFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.approveContract(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void triggerPaymentHandlingFailsWhenAccountNotLinked() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);
        setContractState(response.getId(), ContractState.APPROVED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.triggerPaymentHandling(response.getId(), OTHER_ACCOUNT_ID));
        assertEquals(403, ex.getResponse().getStatus());
    }
}
