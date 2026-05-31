package dev.abstratium.product.service;

import dev.abstratium.core.service.JwtOrgResolver;
import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProductDefinitionServiceTest {

    @Inject
    ProductDefinitionService service;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void setUp() throws Exception {
        userTransaction.begin();
        try {
            createTestProduct("PROD-001", "Test Product 1", ProductDefinition.BillingModel.FIXED_PRICE);
            createTestProduct("PROD-002", "Test Product 2", ProductDefinition.BillingModel.SUBSCRIPTION);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        userTransaction.begin();
        try {
            em.createQuery("DELETE FROM PartAttributeAllowedValue").executeUpdate();
            em.createQuery("DELETE FROM PartAttributeDefinition").executeUpdate();
            em.createQuery("DELETE FROM PartDefinition").executeUpdate();
            em.createQuery("DELETE FROM ProductDefinition").executeUpdate();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private ProductDefinition createTestProduct(String code, String description, ProductDefinition.BillingModel billingModel) {
        ProductDefinition product = new ProductDefinition();
        product.setId(UUID.randomUUID().toString());
        product.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        product.setProductCode(code);
        product.setDescription(description);
        product.setBillingModel(billingModel);
        product.setProductValidFrom(LocalDate.now());
        em.persist(product);
        return product;
    }

    @Test
    void shouldCreateProductDefinition() {
        ProductDefinition product = new ProductDefinition();
        product.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        product.setProductCode("PROD-NEW");
        product.setDescription("New Test Product");
        product.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        product.setProductValidFrom(LocalDate.now());
        product.setProductValidUntil(LocalDate.now().plusYears(1));

        ProductDefinition created = service.createProductDefinition(product);

        assertNotNull(created.getId());
        assertEquals("PROD-NEW", created.getProductCode());
        assertEquals("New Test Product", created.getDescription());
        assertEquals(ProductDefinition.BillingModel.FIXED_PRICE, created.getBillingModel());
    }

    @Test
    void shouldFindById() {
        List<ProductDefinition> all = service.findAll();
        assertFalse(all.isEmpty());

        String id = all.get(0).getId();
        Optional<ProductDefinition> found = service.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    void shouldFindByProductCode() {
        Optional<ProductDefinition> found = service.findByProductCode("PROD-001");

        assertTrue(found.isPresent());
        assertEquals("PROD-001", found.get().getProductCode());
        assertEquals("Test Product 1", found.get().getDescription());
    }

    @Test
    void shouldFindByBillingModel() {
        List<ProductDefinition> fixedPriceProducts = service.findByBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);

        assertFalse(fixedPriceProducts.isEmpty());
        assertTrue(fixedPriceProducts.stream()
            .allMatch(p -> p.getBillingModel() == ProductDefinition.BillingModel.FIXED_PRICE));
    }

    @Test
    void shouldReturnAllProducts() {
        List<ProductDefinition> all = service.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void shouldCheckExistenceByProductCode() {
        assertTrue(service.existsByProductCode("PROD-001"));
        assertTrue(service.existsByProductCode("PROD-002"));
        assertFalse(service.existsByProductCode("NON-EXISTENT"));
    }

    @Test
    void shouldUpdateProductDefinition() throws Exception {
        Optional<ProductDefinition> existing = service.findByProductCode("PROD-001");
        assertTrue(existing.isPresent());

        ProductDefinition product = existing.get();
        product.setDescription("Updated Description");

        userTransaction.begin();
        try {
            ProductDefinition updated = service.updateProductDefinition(product);
            userTransaction.commit();

            assertEquals("Updated Description", updated.getDescription());

            Optional<ProductDefinition> found = service.findById(product.getId());
            assertTrue(found.isPresent());
            assertEquals("Updated Description", found.get().getDescription());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldDeleteProductDefinition() {
        Optional<ProductDefinition> existing = service.findByProductCode("PROD-001");
        assertTrue(existing.isPresent());

        String id = existing.get().getId();
        String code = existing.get().getProductCode();

        service.deleteProductDefinition(id);

        assertFalse(service.existsByProductCode(code));
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<ProductDefinition> found = service.findById("non-existent-id");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentProductCode() {
        Optional<ProductDefinition> found = service.findByProductCode("NON-EXISTENT-CODE");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldPreservePreSetIdOnCreate() {
        String preSetId = UUID.randomUUID().toString();
        ProductDefinition product = new ProductDefinition();
        product.setId(preSetId);
        product.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        product.setProductCode("PRESET-ID-PROD");
        product.setDescription("Product with pre-set ID");
        product.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        product.setProductValidFrom(LocalDate.now());

        ProductDefinition created = service.createProductDefinition(product);

        assertEquals(preSetId, created.getId());
    }

    @Test
    void shouldSilentlyHandleDeleteOfNonExistentId() {
        service.deleteProductDefinition("non-existent-id-that-does-not-exist");
        // No exception expected — delete of non-existent ID is a no-op
    }
}
