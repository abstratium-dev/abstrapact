package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.non_multitenancy.contract.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.non_multitenancy.contract.boundary.dto.PartInstanceRequest;
import dev.abstratium.product.boundary.dto.PartAttributeAllowedValueRequest;
import dev.abstratium.product.boundary.dto.PartAttributeRequest;
import dev.abstratium.product.boundary.dto.PartRequest;
import dev.abstratium.product.boundary.dto.ProductDefinitionRequest;
import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.PartDefinitionChoiceGroup;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.non_multitenancy.NonMultitenancyProductDefinition;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NonMultitenancyCustomerProductInstanceServiceTest {

    @Inject
    NonMultitenancyCustomerProductInstanceService service;

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction tx;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String ppc(String raw) {
        return OrgScopedCodec.encode(defaultOrgId, raw, "Part");
    }

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() {
        currentOrgContext.setOrgId(defaultOrgId);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void createsProductInstanceWithSimplePartTree() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-PROD-001");
        request.setDescription("Product Instance Test Product");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PI-PART-001");
        part.setDescription("Main Part");
        part.setUnitPrice(new BigDecimal("99.99"));
        part.setDisplayOrder(1);
        part.setMinCardinality(1);
        part.setMaxCardinality(1);

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);

        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("PI-PART-001"));

        var productInstance = service.createProductInstance(ntpd, List.of(piReq), defaultOrgId);

        assertNotNull(productInstance.getId());
        assertEquals(1, productInstance.getPartInstances().size());
        assertEquals(0, new BigDecimal("99.99").compareTo(service.calculateLineTotal(productInstance)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsWhenRequiredAttributeMissing() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-ATTR-PROD-001");
        request.setDescription("Product with required attribute");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartAttributeRequest attrReq = new PartAttributeRequest();
        attrReq.setAttributeName("COLOR");
        attrReq.setDataType(dev.abstratium.product.entity.PartAttributeDefinition.DataType.STRING);
        attrReq.setIsRequired(true);

        PartRequest part = new PartRequest();
        part.setPartCode("PI-ATTR-PART-001");
        part.setDescription("Part with attribute");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);
        part.setAttributes(List.of(attrReq));

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("PI-ATTR-PART-001"));
        // No attribute values supplied

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsUnknownAttributeName() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-UNKNOWN-ATTR-PROD-001");
        request.setDescription("Product without attributes");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PI-UNKNOWN-ATTR-PART-001");
        part.setDescription("Part without attributes");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceAttributeRequest attrReq = new PartInstanceAttributeRequest();
        attrReq.setAttributeName("UNKNOWN");
        attrReq.setAttributeValue("RED");

        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("PI-UNKNOWN-ATTR-PART-001"));
        piReq.setAttributeValues(List.of(attrReq));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsDisallowedAttributeValue() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-ALLOWED-PROD-001");
        request.setDescription("Product with allowed values");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartAttributeAllowedValueRequest allowedRed = new PartAttributeAllowedValueRequest();
        allowedRed.setAllowedValue("RED");
        PartAttributeAllowedValueRequest allowedBlue = new PartAttributeAllowedValueRequest();
        allowedBlue.setAllowedValue("BLUE");

        PartAttributeRequest attrReq = new PartAttributeRequest();
        attrReq.setAttributeName("COLOR");
        attrReq.setDataType(dev.abstratium.product.entity.PartAttributeDefinition.DataType.STRING);
        attrReq.setIsRequired(false);
        attrReq.setAllowedValues(List.of(allowedRed, allowedBlue));

        PartRequest part = new PartRequest();
        part.setPartCode("PI-ALLOWED-PART-001");
        part.setDescription("Part with allowed values");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);
        part.setAttributes(List.of(attrReq));

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceAttributeRequest attrReqValue = new PartInstanceAttributeRequest();
        attrReqValue.setAttributeName("COLOR");
        attrReqValue.setAttributeValue("GREEN");

        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("PI-ALLOWED-PART-001"));
        piReq.setAttributeValues(List.of(attrReqValue));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsCardinalityBelowMinimum() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-MIN-CARD-PROD-001");
        request.setDescription("Product requiring two parts");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PI-MIN-CARD-PART-001");
        part.setDescription("Part requiring two instances");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);
        part.setMinCardinality(2);
        part.setMaxCardinality(3);

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        // Only one instance supplied; min is 2
        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("PI-MIN-CARD-PART-001"));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsCardinalityAboveMaximum() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-MAX-CARD-PROD-001");
        request.setDescription("Product allowing one part");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PI-MAX-CARD-PART-001");
        part.setDescription("Part allowing one instance");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);
        part.setMinCardinality(0);
        part.setMaxCardinality(1);

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        // Two instances supplied; max is 1
        PartInstanceRequest piReq1 = new PartInstanceRequest();
        piReq1.setPartCode(ppc("PI-MAX-CARD-PART-001"));
        PartInstanceRequest piReq2 = new PartInstanceRequest();
        piReq2.setPartCode(ppc("PI-MAX-CARD-PART-001"));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq1, piReq2), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void rejectsInvalidPartCode() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-INVALID-PROD-001");
        request.setDescription("Product");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PI-VALID-PART-001");
        part.setDescription("Part");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        request.setParts(List.of(part));

        ProductDefinition created = createProductInTx(request);
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceRequest piReq = new PartInstanceRequest();
        piReq.setPartCode(ppc("NOT-A-VALID-PART"));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(piReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void enforcesChoiceGroupMinChoices() throws Exception {
        ProductDefinition created = createProductWithChoiceGroup();
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        // Parent part requires one child from the choice group, but none supplied
        PartInstanceRequest childReq = new PartInstanceRequest();
        childReq.setPartCode(ppc("PI-CHOICE-CHILD-001"));

        PartInstanceRequest parentReq = new PartInstanceRequest();
        parentReq.setPartCode(ppc("PI-CHOICE-PARENT-001"));
        parentReq.setChildPartInstances(List.of(childReq));

        // Only supply one child when group requires two
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(parentReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void enforcesChoiceGroupMaxChoices() throws Exception {
        ProductDefinition created = createProductWithChoiceGroup();
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceRequest child1 = new PartInstanceRequest();
        child1.setPartCode(ppc("PI-CHOICE-CHILD-001"));
        PartInstanceRequest child2 = new PartInstanceRequest();
        child2.setPartCode(ppc("PI-CHOICE-CHILD-002"));
        PartInstanceRequest child3 = new PartInstanceRequest();
        child3.setPartCode(ppc("PI-CHOICE-CHILD-003"));

        PartInstanceRequest parentReq = new PartInstanceRequest();
        parentReq.setPartCode(ppc("PI-CHOICE-PARENT-001"));
        parentReq.setChildPartInstances(List.of(child1, child2, child3));

        // Group allows max 2 choices
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.createProductInstance(ntpd, List.of(parentReq), defaultOrgId));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void happyPathWithChoiceGroup() throws Exception {
        ProductDefinition created = createProductWithChoiceGroup();
        NonMultitenancyProductDefinition ntpd = em.find(
            NonMultitenancyProductDefinition.class, created.getId());

        PartInstanceRequest child1 = new PartInstanceRequest();
        child1.setPartCode(ppc("PI-CHOICE-CHILD-001"));
        PartInstanceRequest child2 = new PartInstanceRequest();
        child2.setPartCode(ppc("PI-CHOICE-CHILD-002"));

        PartInstanceRequest parentReq = new PartInstanceRequest();
        parentReq.setPartCode(ppc("PI-CHOICE-PARENT-001"));
        parentReq.setChildPartInstances(List.of(child1, child2));

        var productInstance = service.createProductInstance(ntpd, List.of(parentReq), defaultOrgId);

        assertEquals(1, productInstance.getPartInstances().size());
        assertEquals(2, productInstance.getPartInstances().get(0).getChildPartInstances().size());
        // parent 50 + child1 10 + child2 20 = 80
        assertEquals(0, new BigDecimal("80.00").compareTo(service.calculateLineTotal(productInstance)));
    }

    // ==================== helpers ====================

    private ProductDefinition createProductInTx(ProductDefinitionRequest request) throws Exception {
        tx.begin();
        try {
            ProductDefinition created = productDefinitionService.createCompleteProduct(request);
            tx.commit();
            return created;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    private ProductDefinition createProductWithChoiceGroup() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("PI-CHOICE-PROD-001");
        request.setDescription("Product with choice group");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        PartRequest child1 = new PartRequest();
        child1.setPartCode("PI-CHOICE-CHILD-001");
        child1.setDescription("Child 1");
        child1.setUnitPrice(new BigDecimal("10.00"));
        child1.setDisplayOrder(1);
        child1.setMinCardinality(0);
        child1.setMaxCardinality(1);

        PartRequest child2 = new PartRequest();
        child2.setPartCode("PI-CHOICE-CHILD-002");
        child2.setDescription("Child 2");
        child2.setUnitPrice(new BigDecimal("20.00"));
        child2.setDisplayOrder(2);
        child2.setMinCardinality(0);
        child2.setMaxCardinality(1);

        PartRequest child3 = new PartRequest();
        child3.setPartCode("PI-CHOICE-CHILD-003");
        child3.setDescription("Child 3");
        child3.setUnitPrice(new BigDecimal("30.00"));
        child3.setDisplayOrder(3);
        child3.setMinCardinality(0);
        child3.setMaxCardinality(1);

        PartRequest parent = new PartRequest();
        parent.setPartCode("PI-CHOICE-PARENT-001");
        parent.setDescription("Parent");
        parent.setUnitPrice(new BigDecimal("50.00"));
        parent.setDisplayOrder(1);
        parent.setMinCardinality(1);
        parent.setMaxCardinality(1);
        parent.setChildParts(List.of(child1, child2, child3));

        request.setParts(List.of(parent));

        ProductDefinition created = createProductInTx(request);

        // Add choice group manually
        tx.begin();
        try {
            PartDefinition parentPart = em.createQuery(
                    "SELECT p FROM PartDefinition p WHERE p.productDefinition.id = :pid AND p.partCode = :code",
                    PartDefinition.class)
                .setParameter("pid", created.getId())
                .setParameter("code", ppc("PI-CHOICE-PARENT-001"))
                .getSingleResult();

            PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
            group.setId(UUID.randomUUID().toString());
            group.setOrganisationId(defaultOrgId);
            group.setParentPartDefinition(parentPart);
            group.setMinChoices(2);
            group.setMaxChoices(2);
            em.persist(group);

            List<PartDefinition> children = em.createQuery(
                    "SELECT p FROM PartDefinition p WHERE p.parentPart.id = :parentId",
                    PartDefinition.class)
                .setParameter("parentId", parentPart.getId())
                .getResultList();

            for (PartDefinition child : children) {
                child.setChoiceGroup(group);
                em.merge(child);
            }

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }

        return created;
    }
}
