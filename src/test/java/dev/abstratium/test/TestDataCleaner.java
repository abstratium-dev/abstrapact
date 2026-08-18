package dev.abstratium.test;

import dev.abstratium.abstrapact.contracts.entity.Contract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractAccountRole;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.entity.ProductInstance;
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
 *   <li>{@code NonMultitenancyProcessInstance} (deleted first, has FK to Contract)</li>
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
            // Payment tables must be removed before Contract (FK from
            // T_webhook_event -> T_payment_transaction -> T_contract).
            removeAll(WebhookEvent.class);
            removeAll(PaymentTransaction.class);
            removeAll(NonMultitenancyProcessInstance.class);
            // Remove both tenant-bound and non-multitenancy contracts (both map to T_contract;
            // the tenant-bound query is filtered by @TenantId and may miss rows created via
            // the non-multitenancy entity). NonMultitenancyContract cascades to line items,
            // signatories, terms links, and account roles.
            removeAll(NonMultitenancyContractAccountRole.class);
            removeAll(NonMultitenancyContract.class);
            removeAll(Contract.class);
            // Remove both tenant-bound and non-multitenancy product instances/definitions
            // (both map to the same DB tables; the tenant-bound query is filtered by @TenantId
            // and may miss rows created via the non-multitenancy entity).
            removeAll(NonMultitenancyProductInstance.class);
            removeAll(ProductInstance.class);
            removeAll(NonMultitenancyProductDefinition.class);
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
