package dev.abstratium.core.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Helper class to reset the database state before tests.
 * Deletes all test data in the correct order to avoid FK constraint violations.
 * Uses native SQL to bypass Hibernate's @TenantId filter and clean up across ALL orgs.
 */
@ApplicationScoped
public class TestDatabaseResetHelper {

    @Inject
    EntityManager em;

    /**
     * Resets the database by deleting all test-related data.
     * Deletes children before parents to avoid FK constraint violations.
     * Uses native SQL to bypass tenant filtering.
     */
    public void resetDatabase() {
        // Delete in reverse order of dependencies to avoid FK violations

        // for example:
        //
        // 1. Authorization codes (depends on accounts, clients)
        // em.createNativeQuery("DELETE FROM T_authorization_codes WHERE client_id LIKE '%test%' OR account_id IN (SELECT id FROM T_accounts WHERE email LIKE '%@example.com')")
        //     .executeUpdate();

    }
}
