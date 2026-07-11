package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.conditions.non_multitenancy.NonMultitenancyContract;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.non_multitenancy.contract.boundary.dto.*;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.core.service.OrgScopedCodec;
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
class NonMultitenancyCustomerContractServiceTest {

    @Inject
    NonMultitenancyCustomerContractService service;

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

    private static final String ACCOUNT_ID = "test-customer-sub-001";

    private String pc(String raw) {
        return OrgScopedCodec.encode(defaultOrgId, raw, "Product");
    }

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() {
        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("CONTRACT-PROD-001");
        pd.setDescription("Contract Test Product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setProductValidFrom(LocalDate.now());
        pd.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(pd);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private CreateCustomerContractRequest buildRequest(String ref) {
        CustomerLineItemRequest li = new CustomerLineItemRequest();
        li.setProductCode(pc("CONTRACT-PROD-001"));
        li.setDisplayOrder(0);

        CreateCustomerContractRequest req = new CreateCustomerContractRequest();
        req.setOrgId(defaultOrgId);
        req.setContractReference(ref);
        req.setLineItems(List.of(li));
        return req;
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createContractHappyPath() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse resp = service.createContract(
            buildRequest("REF-HAPPY-001"), defaultOrgId, ACCOUNT_ID);

        assertNotNull(resp.getId());
        assertEquals("REF-HAPPY-001", resp.getContractReference());
        assertEquals(ContractState.DRAFT, resp.getState());
        assertEquals(defaultOrgId, resp.getSellerOrganisationId());
        assertEquals(1, resp.getLineItems().size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createdContractIsLinkedToAccount() throws Exception {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse resp = service.createContract(
            buildRequest("REF-LINK-001"), defaultOrgId, ACCOUNT_ID);

        tx.begin();
        long count = em.createQuery(
                "SELECT COUNT(r) FROM NonMultitenancyContractAccountRole r " +
                "WHERE r.contract.id = :cid AND r.accountId = :aid",
                Long.class)
            .setParameter("cid", resp.getId())
            .setParameter("aid", ACCOUNT_ID)
            .getSingleResult();
        tx.commit();

        assertEquals(1L, count);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void listContractsReturnsOnlyCallerContracts() {
        currentOrgContext.setOrgId(defaultOrgId);

        service.createContract(buildRequest("REF-LIST-001"), defaultOrgId, ACCOUNT_ID);

        List<CustomerContractSummary> summaries = service.listContracts(ACCOUNT_ID, null);
        assertTrue(summaries.stream().anyMatch(s -> "REF-LIST-001".equals(s.getContractReference())));

        List<CustomerContractSummary> otherSummaries = service.listContracts("other-account", null);
        assertTrue(otherSummaries.stream().noneMatch(s -> "REF-LIST-001".equals(s.getContractReference())));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void getContractForbiddenForOtherAccount() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse resp = service.createContract(
            buildRequest("REF-FORBIDDEN-001"), defaultOrgId, ACCOUNT_ID);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.getContract(resp.getId(), "wrong-account"));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void updateContractReplacesLineItems() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse created = service.createContract(
            buildRequest("REF-UPDATE-001"), defaultOrgId, ACCOUNT_ID);

        CustomerLineItemRequest newLi = new CustomerLineItemRequest();
        newLi.setProductCode(pc("CONTRACT-PROD-001"));
        newLi.setDisplayOrder(0);

        CreateCustomerContractRequest updateReq = new CreateCustomerContractRequest();
        updateReq.setContractReference("REF-UPDATE-001-MODIFIED");
        updateReq.setLineItems(List.of(newLi));

        CustomerContractResponse updated = service.updateContract(
            created.getId(), updateReq, defaultOrgId, ACCOUNT_ID);

        assertEquals("REF-UPDATE-001-MODIFIED", updated.getContractReference());
        assertEquals(1, updated.getLineItems().size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void updateContractFailsWhenNotDraft() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse created = service.createContract(
            buildRequest("REF-OFFERED-001"), defaultOrgId, ACCOUNT_ID);

        salesProcessBeanOffer(created.getId());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.updateContract(created.getId(), buildRequest("X"), defaultOrgId, ACCOUNT_ID));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void deleteLineItemReducesTotal() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse created = service.createContract(
            buildRequest("REF-DELETE-LI-001"), defaultOrgId, ACCOUNT_ID);

        String lineItemId = created.getLineItems().get(0).getId();

        CustomerContractResponse updated = service.deleteLineItem(
            created.getId(), lineItemId, defaultOrgId, ACCOUNT_ID);

        assertEquals(0, updated.getLineItems().size());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void getOrgIdForContractReturnsCorrectOrgId() {
        currentOrgContext.setOrgId(defaultOrgId);

        CustomerContractResponse created = service.createContract(
            buildRequest("REF-ORG-001"), defaultOrgId, ACCOUNT_ID);

        String orgId = service.getOrgIdForContract(created.getId());
        assertEquals(defaultOrgId, orgId);
    }

    @Test
    void getOrgIdForContractThrows404WhenNotFound() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.getOrgIdForContract("does-not-exist"));
        assertEquals(404, ex.getResponse().getStatus());
    }

    private void salesProcessBeanOffer(String contractId) {
        try {
            tx.begin();
            NonMultitenancyContract c = em.find(NonMultitenancyContract.class, contractId);
            c.setState(ContractState.OFFERED);
            em.merge(c);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
