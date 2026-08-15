package dev.abstratium.core.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Helper class to reset the database state before tests.
 * Deletes all test data in the correct order to avoid FK constraint violations.
 * Uses raw JDBC to bypass Hibernate's @TenantId filter entirely, so it can be
 * called from contexts where no tenant identifier is available (e.g. {@code @AfterAll}).
 *
 * <p>Migration-seeded data (e.g. the default {@code ABSTRATIUM-001} terms inserted by
 * {@code V01.012}) is preserved so that other tests which depend on it continue to work
 * within the same JVM.</p>
 */
@ApplicationScoped
public class TestDatabaseResetHelper {

    /** ID of the terms-and-conditions row seeded by V01.012 — must not be deleted. */
    private static final String SEEDED_TERMS_ID = "11111111-1111-1111-1111-111111111111";

    @Inject
    DataSource dataSource;

    /**
     * Resets the database by deleting all test-related data.
     * Deletes children before parents to avoid FK constraint violations.
     * Uses raw JDBC to bypass Hibernate tenant filtering entirely.
     */
    public void resetDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Remove audit rows for test-created terms (FK to REVINFO, not to the
                //    base table, so safe to delete first).
                executeUpdate(conn,
                    "DELETE FROM T_terms_and_conditions_AUD WHERE id <> ?",
                    SEEDED_TERMS_ID);

                // 2. Remove any contract-terms links pointing at test-created terms.
                //    TestDataCleaner already cascades Contract removal to ContractTermsLink,
                //    but this protects against leaked rows from other test classes.
                executeUpdate(conn,
                    "DELETE FROM T_contract_terms_link WHERE terms_and_conditions_id <> ?",
                    SEEDED_TERMS_ID);

                // 3. Finally delete the test-created terms themselves, preserving the
                //    migration-seeded row.
                executeUpdate(conn,
                    "DELETE FROM T_terms_and_conditions WHERE id <> ?",
                    SEEDED_TERMS_ID);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset database", e);
        }
    }

    private void executeUpdate(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        }
    }
}
