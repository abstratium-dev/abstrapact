package dev.abstratium.product.service;

import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.product.boundary.dto.*;
import dev.abstratium.product.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

@ApplicationScoped
public class ProductDefinitionService {

    @Inject
    EntityManager em;

    @Inject
    CurrentOrgContext currentOrgContext;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    // ==================== Product Definition ====================

    private String prefixedCode(String rawCode) {
        if (OrgScopedCodec.isPrefixed(rawCode)) {
            return rawCode;
        }
        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            orgId = defaultOrgId;
        }
        return OrgScopedCodec.encode(orgId, rawCode, "Product");
    }

    @Transactional
    public ProductDefinition createProductDefinition(ProductDefinition definition) {
        if (definition.getId() == null) {
            definition.setId(UUID.randomUUID().toString());
        }
        if (definition.getPaymentModel() == null) {
            definition.setPaymentModel(ProductDefinition.PaymentModel.PREPAID);
        }
        definition.setProductCode(prefixedCode(definition.getProductCode()));
        definition.setTermsAndConditionsCode(prefixedTacCode(definition.getTermsAndConditionsCode()));
        em.persist(definition);
        return definition;
    }

    @Transactional
    public ProductDefinition updateProductDefinition(ProductDefinition definition) {
        if (definition.getPaymentModel() == null) {
            ProductDefinition existing = em.find(ProductDefinition.class, definition.getId());
            definition.setPaymentModel(existing != null && existing.getPaymentModel() != null
                ? existing.getPaymentModel()
                : ProductDefinition.PaymentModel.PREPAID);
        }
        definition.setProductCode(prefixedCode(definition.getProductCode()));
        definition.setTermsAndConditionsCode(prefixedTacCode(definition.getTermsAndConditionsCode()));
        return em.merge(definition);
    }

    @Transactional
    public void deleteProductDefinition(String id) {
        ProductDefinition definition = em.find(ProductDefinition.class, id);
        if (definition != null) {
            em.remove(definition);
            em.flush();
        }
    }

    public Optional<ProductDefinition> findById(String id) {
        return Optional.ofNullable(em.find(ProductDefinition.class, id));
    }

    public Optional<ProductDefinition> findByProductCode(String productCode) {
        return em.createQuery(
                "SELECT p FROM ProductDefinition p WHERE p.productCode = :productCode",
                ProductDefinition.class)
            .setParameter("productCode", productCode)
            .getResultStream()
            .findFirst();
    }

    public List<ProductDefinition> findAll() {
        return em.createQuery(
                "SELECT p FROM ProductDefinition p ORDER BY p.productCode",
                ProductDefinition.class)
            .getResultList();
    }

    public List<ProductDefinition> findByBillingModel(ProductDefinition.BillingModel billingModel) {
        return em.createQuery(
                "SELECT p FROM ProductDefinition p WHERE p.billingModel = :billingModel ORDER BY p.productCode",
                ProductDefinition.class)
            .setParameter("billingModel", billingModel)
            .getResultList();
    }

    public boolean existsByProductCode(String productCode) {
        Long count = em.createQuery(
                "SELECT COUNT(p) FROM ProductDefinition p WHERE p.productCode = :productCode",
                Long.class)
            .setParameter("productCode", productCode)
            .getSingleResult();
        return count > 0;
    }

    public boolean existsByRawProductCode(String rawProductCode) {
        return existsByProductCode(prefixedCode(rawProductCode));
    }

    // ==================== Complete Product with Parts ====================

    @Transactional
    public ProductDefinition createCompleteProduct(ProductDefinitionRequest request) {
        ProductDefinition product = new ProductDefinition();
        product.setId(UUID.randomUUID().toString());
        product.setProductCode(prefixedCode(request.getProductCode()));
        product.setDescription(request.getDescription());
        product.setBillingModel(request.getBillingModel());
        product.setPaymentModel(request.getPaymentModel() != null ? request.getPaymentModel() : ProductDefinition.PaymentModel.PREPAID);
        product.setProductValidFrom(request.getProductValidFrom());
        product.setProductValidUntil(request.getProductValidUntil());
        product.setTermsAndConditionsCode(prefixedTacCode(request.getTermsAndConditionsCode()));
        em.persist(product);

        if (request.getParts() != null) {
            for (PartRequest partRequest : request.getParts()) {
                createPartRecursive(product, partRequest, null);
            }
        }

        return product;
    }

    @Transactional
    public ProductDefinition updateCompleteProduct(String id, ProductDefinitionRequest request) {
        ProductDefinition product = em.find(ProductDefinition.class, id);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + id);
        }

        product.setProductCode(prefixedCode(request.getProductCode()));
        product.setDescription(request.getDescription());
        product.setBillingModel(request.getBillingModel());
        product.setPaymentModel(request.getPaymentModel() != null ? request.getPaymentModel() : product.getPaymentModel());
        product.setProductValidFrom(request.getProductValidFrom());
        product.setProductValidUntil(request.getProductValidUntil());
        product.setTermsAndConditionsCode(prefixedTacCode(request.getTermsAndConditionsCode()));
        product = em.merge(product);

        // Delete existing parts and recreate
        deletePartsForProduct(product);

        if (request.getParts() != null) {
            for (PartRequest partRequest : request.getParts()) {
                createPartRecursive(product, partRequest, null);
            }
        }

        return product;
    }

    @Transactional
    public void deleteCompleteProduct(String id) {
        ProductDefinition product = em.find(ProductDefinition.class, id);
        if (product != null) {
            deletePartsForProduct(product);
            em.remove(product);
            em.flush();
        }
    }

    public Optional<ProductDefinition> findCompleteById(String id) {
        return Optional.ofNullable(em.find(ProductDefinition.class, id));
    }

    public List<PartDefinition> findPartsByProductId(String productId) {
        return em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.productDefinition.id = :productId AND p.parentPart IS NULL ORDER BY p.displayOrder",
                PartDefinition.class)
            .setParameter("productId", productId)
            .getResultList();
    }

    @Transactional
    public CompleteProductResponse findCompleteProduct(String productId) {
        ProductDefinition product = em.find(ProductDefinition.class, productId);
        if (product == null) {
            return null;
        }

        CompleteProductResponse response = new CompleteProductResponse();
        response.setId(product.getId());
        response.setProductCode(product.getProductCode());
        response.setDescription(product.getDescription());
        response.setBillingModel(product.getBillingModel());
        response.setPaymentModel(product.getPaymentModel());
        response.setProductValidFrom(product.getProductValidFrom());
        response.setProductValidUntil(product.getProductValidUntil());
        response.setTermsAndConditionsCode(product.getTermsAndConditionsCode());

        List<PartDefinition> rootParts = findPartsByProductId(productId);
        response.setParts(rootParts.stream()
            .map(this::mapPartToResponse)
            .toList());

        return response;
    }

    private PartResponse mapPartToResponse(PartDefinition part) {
        PartResponse response = new PartResponse();
        response.setId(part.getId());
        response.setPartCode(part.getPartCode());
        response.setDescription(part.getDescription());
        response.setUnitPrice(part.getUnitPrice());
        response.setDisplayOrder(part.getDisplayOrder());
        response.setMinCardinality(part.getMinCardinality());
        response.setMaxCardinality(part.getMaxCardinality());

        // Force initialization of lazy collections within transaction
        response.setAttributes(part.getAttributes().stream()
            .map(this::mapAttributeToResponse)
            .toList());

        response.setChildParts(part.getChildParts().stream()
            .map(this::mapPartToResponse)
            .toList());

        return response;
    }

    private PartAttributeResponse mapAttributeToResponse(PartAttributeDefinition attr) {
        PartAttributeResponse response = new PartAttributeResponse();
        response.setId(attr.getId());
        response.setAttributeName(attr.getAttributeName());
        response.setDataType(attr.getDataType());
        response.setIsRequired(attr.getIsRequired());
        response.setDefaultValue(attr.getDefaultValue());

        response.setAllowedValues(attr.getAllowedValues().stream()
            .map(this::mapAllowedValueToResponse)
            .toList());

        return response;
    }

    private AllowedValueResponse mapAllowedValueToResponse(PartAttributeAllowedValue val) {
        AllowedValueResponse response = new AllowedValueResponse();
        response.setId(val.getId());
        response.setAllowedValue(val.getAllowedValue());
        return response;
    }

    private String prefixedPartCode(String rawCode) {
        if (OrgScopedCodec.isPrefixed(rawCode)) {
            return rawCode;
        }
        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            orgId = defaultOrgId;
        }
        return OrgScopedCodec.encode(orgId, rawCode, "Part");
    }

    private String prefixedTacCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return rawCode;
        }
        if (OrgScopedCodec.isPrefixed(rawCode)) {
            return rawCode;
        }
        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            orgId = defaultOrgId;
        }
        return OrgScopedCodec.encode(orgId, rawCode, "Conditions");
    }

    // ==================== Part Management ====================

    private PartDefinition createPartRecursive(ProductDefinition product, PartRequest request, PartDefinition parentPart) {
        PartDefinition part = new PartDefinition();
        part.setId(request.getId() != null ? request.getId() : UUID.randomUUID().toString());
        part.setPartCode(prefixedPartCode(request.getPartCode()));
        part.setDescription(request.getDescription());
        part.setUnitPrice(request.getUnitPrice() != null ? request.getUnitPrice() : java.math.BigDecimal.ZERO);
        part.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        part.setMinCardinality(request.getMinCardinality() != null ? request.getMinCardinality() : 1);
        part.setMaxCardinality(request.getMaxCardinality() != null ? request.getMaxCardinality() : 1);
        part.setProductDefinition(product);
        part.setParentPart(parentPart);
        em.persist(part);

        // Create attributes
        if (request.getAttributes() != null) {
            for (PartAttributeRequest attrRequest : request.getAttributes()) {
                createAttribute(part, attrRequest);
            }
        }

        // Create child parts recursively
        if (request.getChildParts() != null) {
            for (PartRequest childRequest : request.getChildParts()) {
                createPartRecursive(product, childRequest, part);
            }
        }

        return part;
    }

    private PartAttributeDefinition createAttribute(PartDefinition part, PartAttributeRequest request) {
        PartAttributeDefinition attr = new PartAttributeDefinition();
        attr.setId(request.getId() != null ? request.getId() : UUID.randomUUID().toString());
        attr.setAttributeName(request.getAttributeName());
        attr.setDataType(request.getDataType());
        attr.setIsRequired(request.getIsRequired() != null ? request.getIsRequired() : false);
        attr.setDefaultValue(request.getDefaultValue());
        attr.setPartDefinition(part);
        em.persist(attr);

        // Create allowed values
        if (request.getAllowedValues() != null) {
            for (PartAttributeAllowedValueRequest valRequest : request.getAllowedValues()) {
                createAllowedValue(attr, valRequest);
            }
        }

        return attr;
    }

    private PartAttributeAllowedValue createAllowedValue(PartAttributeDefinition attribute, PartAttributeAllowedValueRequest request) {
        PartAttributeAllowedValue value = new PartAttributeAllowedValue();
        value.setId(request.getId() != null ? request.getId() : UUID.randomUUID().toString());
        value.setAllowedValue(request.getAllowedValue());
        value.setAttributeDefinition(attribute);
        em.persist(value);
        return value;
    }

    private void deletePartsForProduct(ProductDefinition product) {
        // First delete child parts (those with a parent)
        List<PartDefinition> childParts = em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.productDefinition.id = :productId AND p.parentPart IS NOT NULL",
                PartDefinition.class)
            .setParameter("productId", product.getId())
            .getResultList();

        for (PartDefinition part : childParts) {
            em.remove(part);
        }

        // Then delete parent parts (those without a parent)
        List<PartDefinition> parentParts = em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.productDefinition.id = :productId AND p.parentPart IS NULL",
                PartDefinition.class)
            .setParameter("productId", product.getId())
            .getResultList();

        for (PartDefinition part : parentParts) {
            em.remove(part);
        }

        em.flush();
    }

    // ==================== Individual Entity Operations ====================

    public Optional<PartDefinition> findPartById(String id) {
        return em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.id = :id",
                PartDefinition.class)
            .setParameter("id", id)
            .getResultStream()
            .findFirst();
    }

    @Transactional
    public PartDefinition createPart(PartDefinition part) {
        if (part.getId() == null) {
            part.setId(UUID.randomUUID().toString());
        }
        part.setPartCode(prefixedPartCode(part.getPartCode()));
        em.persist(part);
        return part;
    }

    @Transactional
    public PartDefinition updatePart(PartDefinition part) {
        part.setPartCode(prefixedPartCode(part.getPartCode()));
        return em.merge(part);
    }

    @Transactional
    public void deletePart(String id) {
        PartDefinition part = em.find(PartDefinition.class, id);
        if (part != null) {
            em.remove(part);
            em.flush();
        }
    }

    public Optional<PartAttributeDefinition> findAttributeById(String id) {
        return Optional.ofNullable(em.find(PartAttributeDefinition.class, id));
    }

    @Transactional
    public PartAttributeDefinition createAttribute(PartAttributeDefinition attribute) {
        if (attribute.getId() == null) {
            attribute.setId(UUID.randomUUID().toString());
        }
        em.persist(attribute);
        return attribute;
    }

    @Transactional
    public PartAttributeDefinition updateAttribute(PartAttributeDefinition attribute) {
        return em.merge(attribute);
    }

    @Transactional
    public void deleteAttribute(String id) {
        PartAttributeDefinition attribute = em.find(PartAttributeDefinition.class, id);
        if (attribute != null) {
            em.remove(attribute);
            em.flush();
        }
    }

    public List<PartAttributeDefinition> findAttributesByPartId(String partId) {
        return em.createQuery(
                "SELECT a FROM PartAttributeDefinition a WHERE a.partDefinition.id = :partId",
                PartAttributeDefinition.class)
            .setParameter("partId", partId)
            .getResultList();
    }
}
