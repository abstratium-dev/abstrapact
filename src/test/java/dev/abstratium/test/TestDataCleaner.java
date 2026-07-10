package dev.abstratium.test;

import dev.abstratium.conditions.entity.Contract;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.entity.ProductInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import java.util.List;

/**
 * Utility for cleaning up test data between test runs.
 *
 * Deletes entities in dependency order, relying on JPA {@code CascadeType.REMOVE}
 * to handle child entities automatically:
 * <ul>
 *   <li>{@code Contract} cascades to: {@code ContractLineItem}, {@code ContractTermsLink},
 *       {@code Signatory}, {@code ContractAccountRole}</li>
 *   <li>{@code ProductInstance} cascades to: {@code PartInstance} (and its children),
 *       {@code PartInstanceAttribute}</li>
 *   <li>{@code ProductDefinition} cascades to: {@code PartDefinition} (and its children),
 *       {@code PartAttributeDefinition}, {@code PartAttributeAllowedValue}</li>
 * </ul>
 *
 * Usage in a {@code @QuarkusTest}:
 * <pre>
 *   {@literal @}Inject TestDataCleaner cleaner;
 *
 *   {@literal @}AfterEach
 *   void tearDown() throws Exception {
 *       cleaner.deleteAll();
 *   }
 * </pre>
 */
@ApplicationScoped
public class TestDataCleaner {

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    public void deleteAll() throws Exception {
        userTransaction.begin();
        try {
            removeAll(Contract.class);
            removeAll(ProductInstance.class);
            removeAll(ProductDefinition.class);
            em.flush();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private <T> void removeAll(Class<T> entityClass) {
        List<T> entities = em.createQuery("SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass)
            .getResultList();
        for (T entity : entities) {
            em.remove(entity);
        }
    }
}
