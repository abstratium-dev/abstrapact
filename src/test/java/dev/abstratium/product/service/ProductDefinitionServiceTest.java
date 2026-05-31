package dev.abstratium.product.service;

import dev.abstratium.core.service.JwtOrgResolver;
import dev.abstratium.product.boundary.dto.*;
import dev.abstratium.product.entity.PartAttributeDefinition;
import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.ProductDefinition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
    void setUp() {
        // Create test products using the service which handles transactions
        ProductDefinition product1 = new ProductDefinition();
        product1.setId(UUID.randomUUID().toString());
        product1.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        product1.setProductCode("PROD-001");
        product1.setDescription("Test Product 1");
        product1.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        product1.setProductValidFrom(LocalDate.now());
        service.createProductDefinition(product1);

        ProductDefinition product2 = new ProductDefinition();
        product2.setId(UUID.randomUUID().toString());
        product2.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        product2.setProductCode("PROD-002");
        product2.setDescription("Test Product 2");
        product2.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        product2.setProductValidFrom(LocalDate.now());
        service.createProductDefinition(product2);
    }

    @AfterEach
    void tearDown() throws Exception {
        userTransaction.begin();
        try {
            // Delete in correct order respecting foreign keys
            // 1. Delete allowed values
            em.createQuery("DELETE FROM PartAttributeAllowedValue").executeUpdate();
            // 2. Delete attributes
            em.createQuery("DELETE FROM PartAttributeDefinition").executeUpdate();
            // 3. Delete child parts first (where parent_part_definition_id is not null)
            em.createQuery("DELETE FROM PartDefinition p WHERE p.parentPart IS NOT NULL").executeUpdate();
            // 4. Delete parent parts
            em.createQuery("DELETE FROM PartDefinition").executeUpdate();
            // 5. Delete products
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
        return service.createProductDefinition(product);
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

    // ==================== Complete Product with Parts Tests ====================

    @Test
    void shouldCreateCompleteProductWithParts() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("COMPLETE-PROD-001");
        request.setDescription("Complete Product with Parts");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());
        request.setProductValidUntil(LocalDate.now().plusYears(1));

        // Add parts
        List<PartRequest> parts = new ArrayList<>();
        PartRequest part1 = new PartRequest();
        part1.setPartCode("PART-001");
        part1.setDescription("Main Part");
        part1.setUnitPrice(new BigDecimal("99.99"));
        part1.setDisplayOrder(1);

        // Add attributes to part
        List<PartAttributeRequest> attrs = new ArrayList<>();
        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("COLOR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(true);

        // Add allowed values
        List<PartAttributeAllowedValueRequest> values = new ArrayList<>();
        PartAttributeAllowedValueRequest val1 = new PartAttributeAllowedValueRequest();
        val1.setAllowedValue("RED");
        values.add(val1);
        PartAttributeAllowedValueRequest val2 = new PartAttributeAllowedValueRequest();
        val2.setAllowedValue("BLUE");
        values.add(val2);
        attr.setAllowedValues(values);
        attrs.add(attr);
        part1.setAttributes(attrs);
        parts.add(part1);

        // Add child part
        PartRequest childPart = new PartRequest();
        childPart.setPartCode("PART-001-CHILD");
        childPart.setDescription("Child Component");
        childPart.setUnitPrice(new BigDecimal("49.99"));
        childPart.setDisplayOrder(1);
        List<PartRequest> children = new ArrayList<>();
        children.add(childPart);
        part1.setChildParts(children);

        request.setParts(parts);

        userTransaction.begin();
        try {
            ProductDefinition created = service.createCompleteProduct(request);
            userTransaction.commit();

            assertNotNull(created.getId());
            assertEquals("COMPLETE-PROD-001", created.getProductCode());

            // Verify parts were created
            List<PartDefinition> productParts = service.findPartsByProductId(created.getId());
            assertEquals(1, productParts.size());
            assertEquals("PART-001", productParts.get(0).getPartCode());

            // Verify attributes
            List<PartAttributeDefinition> attributes = service.findAttributesByPartId(productParts.get(0).getId());
            assertEquals(1, attributes.size());
            assertEquals("COLOR", attributes.get(0).getAttributeName());

            // Verify allowed values
            assertEquals(2, attributes.get(0).getAllowedValues().size());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldUpdateCompleteProductWithParts() throws Exception {
        // First create a product
        ProductDefinitionRequest createRequest = new ProductDefinitionRequest();
        createRequest.setProductCode("UPDATE-COMPLETE-001");
        createRequest.setDescription("Original Description");
        createRequest.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        createRequest.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("ORIGINAL-PART");
        part.setDescription("Original Part");
        part.setUnitPrice(new BigDecimal("50.00"));
        part.setDisplayOrder(1);
        parts.add(part);
        createRequest.setParts(parts);

        userTransaction.begin();
        ProductDefinition created;
        try {
            created = service.createCompleteProduct(createRequest);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        String productId = created.getId();

        // Now update the product with new parts
        ProductDefinitionRequest updateRequest = new ProductDefinitionRequest();
        updateRequest.setProductCode("UPDATE-COMPLETE-001");
        updateRequest.setDescription("Updated Description");
        updateRequest.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        updateRequest.setProductValidFrom(LocalDate.now());

        List<PartRequest> newParts = new ArrayList<>();
        PartRequest newPart = new PartRequest();
        newPart.setPartCode("UPDATED-PART");
        newPart.setDescription("Updated Part");
        newPart.setUnitPrice(new BigDecimal("75.00"));
        newPart.setDisplayOrder(1);
        newParts.add(newPart);
        updateRequest.setParts(newParts);

        userTransaction.begin();
        try {
            ProductDefinition updated = service.updateCompleteProduct(productId, updateRequest);
            userTransaction.commit();

            assertEquals("Updated Description", updated.getDescription());
            assertEquals(ProductDefinition.BillingModel.SUBSCRIPTION, updated.getBillingModel());

            // Verify parts were replaced
            List<PartDefinition> productParts = service.findPartsByProductId(productId);
            assertEquals(1, productParts.size());
            assertEquals("UPDATED-PART", productParts.get(0).getPartCode());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldDeleteCompleteProductWithAllParts() throws Exception {
        // Create product with parts
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("DELETE-COMPLETE-001");
        request.setDescription("To be deleted");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setProductValidFrom(LocalDate.now());

        List<PartRequest> parts = new ArrayList<>();
        PartRequest part = new PartRequest();
        part.setPartCode("DELETE-PART");
        part.setDescription("Part to be deleted");
        part.setUnitPrice(new BigDecimal("100.00"));
        part.setDisplayOrder(1);

        List<PartAttributeRequest> attrs = new ArrayList<>();
        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("SIZE");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(false);
        attrs.add(attr);
        part.setAttributes(attrs);
        parts.add(part);
        request.setParts(parts);

        userTransaction.begin();
        ProductDefinition created;
        try {
            created = service.createCompleteProduct(request);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        String productId = created.getId();
        String partId = service.findPartsByProductId(productId).get(0).getId();

        // Delete complete product
        userTransaction.begin();
        try {
            service.deleteCompleteProduct(productId);
            userTransaction.commit();

            // Verify product is deleted
            assertFalse(service.findById(productId).isPresent());

            // Verify parts are also deleted (cascade)
            assertFalse(service.findPartById(partId).isPresent());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldCreatePartIndependently() throws Exception {
        // First create a product
        ProductDefinition product = createTestProduct("PART-TEST-PROD", "Product for Part Test", ProductDefinition.BillingModel.FIXED_PRICE);
        String productId = product.getId();

        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("INDEPENDENT-PART");
        part.setDescription("Independent Part");
        part.setUnitPrice(new BigDecimal("25.00"));
        part.setDisplayOrder(1);

        userTransaction.begin();
        try {
            PartDefinition created = service.createPart(part);
            userTransaction.commit();

            assertNotNull(created.getId());
            assertEquals("INDEPENDENT-PART", created.getPartCode());

            Optional<PartDefinition> found = service.findPartById(created.getId());
            assertTrue(found.isPresent());
            assertEquals("INDEPENDENT-PART", found.get().getPartCode());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldUpdatePart() throws Exception {
        // Create product and part
        ProductDefinition product = createTestProduct("PART-UPDATE-PROD", "Product", ProductDefinition.BillingModel.FIXED_PRICE);
        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("UPDATE-PART");
        part.setDescription("Original");
        part.setUnitPrice(new BigDecimal("10.00"));
        part.setDisplayOrder(1);

        userTransaction.begin();
        PartDefinition created;
        try {
            created = service.createPart(part);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Update the part
        created.setDescription("Updated Description");
        created.setUnitPrice(new BigDecimal("20.00"));

        userTransaction.begin();
        try {
            PartDefinition updated = service.updatePart(created);
            userTransaction.commit();

            assertEquals("Updated Description", updated.getDescription());
            assertEquals(0, updated.getUnitPrice().compareTo(new BigDecimal("20.00")));
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldDeletePart() throws Exception {
        // Create product and part
        ProductDefinition product = createTestProduct("PART-DELETE-PROD", "Product", ProductDefinition.BillingModel.FIXED_PRICE);
        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("DELETE-PART");
        part.setDescription("To Delete");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        userTransaction.begin();
        PartDefinition created;
        try {
            created = service.createPart(part);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        String partId = created.getId();

        // Delete the part
        userTransaction.begin();
        try {
            service.deletePart(partId);
            userTransaction.commit();

            assertFalse(service.findPartById(partId).isPresent());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldCreateAttributeIndependently() throws Exception {
        // Create product and part first
        ProductDefinition product = createTestProduct("ATTR-TEST-PROD", "Product", ProductDefinition.BillingModel.FIXED_PRICE);
        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("ATTR-TEST-PART");
        part.setDescription("Part for Attribute");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        userTransaction.begin();
        try {
            service.createPart(part);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Create attribute
        PartAttributeDefinition attr = new PartAttributeDefinition();
        attr.setId(UUID.randomUUID().toString());
        attr.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        attr.setPartDefinition(part);
        attr.setAttributeName("MATERIAL");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(true);
        attr.setDefaultValue("STEEL");

        userTransaction.begin();
        try {
            PartAttributeDefinition created = service.createAttribute(attr);
            userTransaction.commit();

            assertNotNull(created.getId());
            assertEquals("MATERIAL", created.getAttributeName());

            List<PartAttributeDefinition> attrs = service.findAttributesByPartId(part.getId());
            assertEquals(1, attrs.size());
            assertEquals("MATERIAL", attrs.get(0).getAttributeName());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldUpdateAttribute() throws Exception {
        // Create product, part, and attribute
        ProductDefinition product = createTestProduct("ATTR-UPDATE-PROD", "Product", ProductDefinition.BillingModel.FIXED_PRICE);
        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("ATTR-UPDATE-PART");
        part.setDescription("Part");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        PartAttributeDefinition attr = new PartAttributeDefinition();
        attr.setId(UUID.randomUUID().toString());
        attr.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        attr.setAttributeName("ORIGINAL-ATTR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(false);

        userTransaction.begin();
        try {
            service.createPart(part);
            attr.setPartDefinition(part);
            service.createAttribute(attr);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Update attribute
        attr.setAttributeName("UPDATED-ATTR");
        attr.setIsRequired(true);

        userTransaction.begin();
        try {
            PartAttributeDefinition updated = service.updateAttribute(attr);
            userTransaction.commit();

            assertEquals("UPDATED-ATTR", updated.getAttributeName());
            assertTrue(updated.getIsRequired());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldDeleteAttribute() throws Exception {
        // Create product, part, and attribute
        ProductDefinition product = createTestProduct("ATTR-DELETE-PROD", "Product", ProductDefinition.BillingModel.FIXED_PRICE);
        PartDefinition part = new PartDefinition();
        part.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        part.setProductDefinition(product);
        part.setPartCode("ATTR-DELETE-PART");
        part.setDescription("Part");
        part.setUnitPrice(BigDecimal.ZERO);
        part.setDisplayOrder(1);

        PartAttributeDefinition attr = new PartAttributeDefinition();
        attr.setId(UUID.randomUUID().toString());
        attr.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        attr.setAttributeName("DELETE-ATTR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(false);

        userTransaction.begin();
        try {
            service.createPart(part);
            attr.setPartDefinition(part);
            service.createAttribute(attr);
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        String attrId = attr.getId();

        // Delete attribute
        userTransaction.begin();
        try {
            service.deleteAttribute(attrId);
            userTransaction.commit();

            assertFalse(service.findAttributeById(attrId).isPresent());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldCreateCompleteProductWithoutParts() throws Exception {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("NO-PARTS-001");
        request.setDescription("Product without parts");
        request.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        request.setProductValidFrom(LocalDate.now());
        request.setParts(null); // No parts

        userTransaction.begin();
        try {
            ProductDefinition created = service.createCompleteProduct(request);
            userTransaction.commit();

            assertNotNull(created.getId());
            assertEquals("NO-PARTS-001", created.getProductCode());

            // Verify no parts exist
            List<PartDefinition> productParts = service.findPartsByProductId(created.getId());
            assertTrue(productParts.isEmpty());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentCompleteProduct() {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode("NON-EXISTENT");
        request.setDescription("Does not exist");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);

        assertThrows(IllegalArgumentException.class, () -> {
            service.updateCompleteProduct("non-existent-id", request);
        });
    }
}
