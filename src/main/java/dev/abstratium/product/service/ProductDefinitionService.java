package dev.abstratium.product.service;

import dev.abstratium.product.entity.ProductDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProductDefinitionService {

    @Inject
    EntityManager em;

    @Transactional
    public ProductDefinition createProductDefinition(ProductDefinition definition) {
        if (definition.getId() == null) {
            definition.setId(UUID.randomUUID().toString());
        }
        em.persist(definition);
        return definition;
    }

    @Transactional
    public ProductDefinition updateProductDefinition(ProductDefinition definition) {
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
}
