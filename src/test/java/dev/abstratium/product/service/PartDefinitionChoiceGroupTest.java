package dev.abstratium.product.service;

import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.PartDefinitionChoiceGroup;
import dev.abstratium.product.entity.ProductDefinition;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PartDefinitionChoiceGroupTest {

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private ProductDefinition persistProduct() {
        ProductDefinition pd = new ProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setProductCode("CG-PROD-" + System.currentTimeMillis());
        pd.setDescription("Choice group test product");
        pd.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        em.persist(pd);
        return pd;
    }

    private PartDefinition persistPart(ProductDefinition pd, String code, PartDefinition parent) {
        PartDefinition part = new PartDefinition();
        part.setId(UUID.randomUUID().toString());
        part.setProductDefinition(pd);
        part.setParentPart(parent);
        part.setPartCode(code);
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);
        em.persist(part);
        return part;
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void canPersistChoiceGroupOnParentPart() {
        ProductDefinition pd = persistProduct();
        PartDefinition parentPart = persistPart(pd, "PROCESSOR", null);

        PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
        group.setId(UUID.randomUUID().toString());
        group.setParentPartDefinition(parentPart);
        group.setMinChoices(1);
        group.setMaxChoices(1);
        em.persist(group);
        em.flush();

        PartDefinitionChoiceGroup found = em.find(PartDefinitionChoiceGroup.class, group.getId());
        assertNotNull(found);
        assertEquals(1, found.getMinChoices());
        assertEquals(1, found.getMaxChoices());
        assertEquals(parentPart.getId(), found.getParentPartDefinition().getId());
        assertNotNull(found.getOrganisationId());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void childPartCanBeAssignedToChoiceGroup() {
        ProductDefinition pd = persistProduct();
        PartDefinition parentPart = persistPart(pd, "PROCESSOR", null);

        PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
        group.setId(UUID.randomUUID().toString());
        group.setParentPartDefinition(parentPart);
        group.setMinChoices(1);
        group.setMaxChoices(1);
        em.persist(group);

        PartDefinition child1 = persistPart(pd, "I5", parentPart);
        child1.setChoiceGroup(group);

        PartDefinition child2 = persistPart(pd, "I7", parentPart);
        child2.setChoiceGroup(group);

        PartDefinition independent = persistPart(pd, "WARRANTY", parentPart);

        em.flush();
        em.clear();

        PartDefinition foundChild1 = em.find(PartDefinition.class, child1.getId());
        PartDefinition foundChild2 = em.find(PartDefinition.class, child2.getId());
        PartDefinition foundIndependent = em.find(PartDefinition.class, independent.getId());

        assertNotNull(foundChild1.getChoiceGroup());
        assertEquals(group.getId(), foundChild1.getChoiceGroup().getId());
        assertNotNull(foundChild2.getChoiceGroup());
        assertEquals(group.getId(), foundChild2.getChoiceGroup().getId());
        assertNull(foundIndependent.getChoiceGroup());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void choiceGroupDefaultsAreOneToOne() {
        ProductDefinition pd = persistProduct();
        PartDefinition parentPart = persistPart(pd, "OPTIONS", null);

        PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
        group.setId(UUID.randomUUID().toString());
        group.setParentPartDefinition(parentPart);
        em.persist(group);
        em.flush();

        PartDefinitionChoiceGroup found = em.find(PartDefinitionChoiceGroup.class, group.getId());
        assertEquals(1, found.getMinChoices());
        assertEquals(1, found.getMaxChoices());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void parentPartCanHaveMultipleChoiceGroups() {
        ProductDefinition pd = persistProduct();
        PartDefinition parentPart = persistPart(pd, "BUNDLE", null);

        for (int i = 0; i < 3; i++) {
            PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
            group.setId(UUID.randomUUID().toString());
            group.setParentPartDefinition(parentPart);
            group.setMinChoices(1);
            group.setMaxChoices(2);
            em.persist(group);
        }
        em.flush();

        List<PartDefinitionChoiceGroup> groups = em.createQuery(
                "SELECT g FROM PartDefinitionChoiceGroup g WHERE g.parentPartDefinition.id = :parentId",
                PartDefinitionChoiceGroup.class)
            .setParameter("parentId", parentPart.getId())
            .getResultList();

        assertEquals(3, groups.size());
    }

    @Test
    @Transactional
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void deletingParentPartCascadesToChoiceGroup() {
        ProductDefinition pd = persistProduct();
        PartDefinition parentPart = persistPart(pd, "CASCADE-PARENT", null);
        String parentId = parentPart.getId();

        PartDefinitionChoiceGroup group = new PartDefinitionChoiceGroup();
        group.setId(UUID.randomUUID().toString());
        group.setParentPartDefinition(parentPart);
        em.persist(group);
        em.flush();

        String groupId = group.getId();

        em.clear();
        em.remove(em.find(PartDefinition.class, parentId));
        em.flush();

        assertNull(em.find(PartDefinitionChoiceGroup.class, groupId));
    }
}
