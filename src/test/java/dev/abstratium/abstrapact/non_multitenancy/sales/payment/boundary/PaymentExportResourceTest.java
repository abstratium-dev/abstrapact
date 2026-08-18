package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.quarkus.test.security.oidc.Claim;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration tests for {@link PaymentExportResource}.
 *
 * <p>Tests the CSV export endpoint for successful payment transactions.
 */
@QuarkusTest
class PaymentExportResourceTest {

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String contractId;

    @BeforeEach
    @Transactional
    void setUp() {
        contractId = UUID.randomUUID().toString();

        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(defaultOrgId);
        contract.setContractReference("EXPORT-TEST-" + UUID.randomUUID());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.RUNNING);
        contract.setGrandTotal(new BigDecimal("100.00"));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        // SUCCEEDED transaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(defaultOrgId);
        tx.setContractId(contractId);
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(UUID.randomUUID().toString());
        tx.setPspSessionId("cs_export_1");
        tx.setPspTransactionRef("pi_export_1");
        tx.setGrossAmount(new BigDecimal("100.00"));
        tx.setFeeAmount(new BigDecimal("3.15"));
        tx.setNetAmount(new BigDecimal("96.85"));
        tx.setCurrency("EUR");
        tx.setStatus(PaymentTransaction.PaymentStatus.SUCCEEDED);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        em.persist(tx);

        // PENDING transaction (should NOT appear in export)
        PaymentTransaction tx2 = new PaymentTransaction();
        tx2.setId(UUID.randomUUID().toString());
        tx2.setOrganisationId(defaultOrgId);
        tx2.setContractId(contractId);
        tx2.setPspIdentifier("stripe");
        tx2.setCorrelationId(UUID.randomUUID().toString());
        tx2.setPspSessionId("cs_export_2");
        tx2.setGrossAmount(new BigDecimal("50.00"));
        tx2.setCurrency("EUR");
        tx2.setStatus(PaymentTransaction.PaymentStatus.PENDING);
        tx2.setCreatedAt(LocalDateTime.now());
        tx2.setUpdatedAt(LocalDateTime.now());
        em.persist(tx2);
        em.flush();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = "00000000-0000-0000-0000-000000000000"))
    void exportReturnsCsvWithSucceededTransactionsOnly() {
        String csv = given()
            .when()
            .get("/public/payment/export?from=" + LocalDate.now().minusDays(1)
                + "&to=" + LocalDate.now().plusDays(1))
        .then()
            .statusCode(200)
            .contentType("text/csv")
            .extract().asString();

        // Header row
        assertThat(csv, containsString("date,partner,description,gross_amount,fee_amount,stripe_txn,contract_id"));
        // The SUCCEEDED transaction should be in the export
        assertThat(csv, containsString("pi_export_1"));
        assertThat(csv, containsString(contractId));
        // The PENDING transaction should NOT be in the export
        assertThat(csv, not(containsString("cs_export_2")));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    @OidcSecurity(claims = @Claim(key = "orgId", value = "00000000-0000-0000-0000-000000000000"))
    void exportReturnsEmptyCsvForDateRangeWithNoTransactions() {
        String csv = given()
            .when()
            .get("/public/payment/export?from=2099-01-01&to=2099-01-02")
        .then()
            .statusCode(200)
            .extract().asString();

        // Only the header row
        assertThat(csv, containsString("date,partner,description"));
        // No data rows
        assertThat(csv, not(containsString("pi_export_1")));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void exportFailsWithoutFromDate() {
        given()
            .when()
            .get("/public/payment/export?to=" + LocalDate.now())
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void exportFailsWithoutToDate() {
        given()
            .when()
            .get("/public/payment/export?from=" + LocalDate.now())
        .then()
            .statusCode(400);
    }
}
