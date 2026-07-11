package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.conditions.non_multitenancy.NonMultitenancyContract;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.non_multitenancy.contract.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.non_multitenancy.contract.boundary.dto.CustomerContractResponse;
import dev.abstratium.non_multitenancy.contract.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.process.entity.ProcessInstanceState;
import dev.abstratium.process.non_multitenancy.NonMultitenancyProcessInstance;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.service.ProductDefinitionService;
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
                "SELECT p FROM NonMultitenancyProcessInstance p WHERE p.processName = :name",
                NonMultitenancyProcessInstance.class)
            .setParameter("name", "sales-process:" + contractId)
            .getSingleResult();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void startSalesProcessCreatesProcessInstanceInProgress() {
        CustomerContractResponse response = createDraftContract();

        NonMultitenancyProcessInstance process = loadProcess(response.getId());

        assertEquals(ProcessInstanceState.IN_PROGRESS, process.getState());
        assertEquals(defaultOrgId, process.getOrganisationId());
        assertEquals("sales-process:" + response.getId(), process.getProcessName());
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
    void acceptContractTransitionsOfferedToAccepted() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, response.getId());
        assertEquals(ContractState.ACCEPTED, contract.getState());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractRecordsStep() {
        CustomerContractResponse response = createDraftContract();
        salesProcessService.offerContract(response.getId(), ACCOUNT_ID);

        salesProcessService.acceptContract(response.getId(), ACCOUNT_ID);

        NonMultitenancyProcessInstance process = loadProcess(response.getId());
        long acceptSteps = process.getSteps().stream()
            .filter(s -> ContractState.ACCEPTED.name().equals(s.getToState()))
            .count();
        assertEquals(1, acceptSteps);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void acceptContractFailsWhenNotOffered() {
        CustomerContractResponse response = createDraftContract();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> salesProcessService.acceptContract(response.getId(), ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
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
}
