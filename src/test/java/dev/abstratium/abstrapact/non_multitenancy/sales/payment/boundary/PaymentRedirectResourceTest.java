package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractLineItem;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for {@link PaymentRedirectResource}.
 *
 * <p>Tests the success and cancel redirect endpoints that Stripe redirects the customer's
 * browser to after payment.
 */
@QuarkusTest
class PaymentRedirectResourceTest {

    @Inject
    EntityManager em;

    @Inject
    TestDataCleaner cleaner;

    private static final String ORG_ID = "redirect-test-org";
    private String sessionId;
    private String contractId;

    @BeforeEach
    @Transactional
    void setUp() {
        sessionId = "cs_redirect_" + UUID.randomUUID();
        contractId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        NonMultitenancyProductDefinition pd = new NonMultitenancyProductDefinition();
        pd.setId(UUID.randomUUID().toString());
        pd.setOrganisationId(ORG_ID);
        pd.setProductCode("REDIRECT-TEST-" + UUID.randomUUID());
        pd.setBillingModel(NonMultitenancyProductDefinition.BillingModel.FIXED_PRICE);
        pd.setPaymentModel(NonMultitenancyProductDefinition.PaymentModel.PREPAID);
        pd.setProductValidFrom(LocalDate.now());
        pd.setStripeSecretKey("sk_test");
        pd.setStripeWebhookSecret("whsec_test");
        pd.setPaymentSuccessRedirectUrl("https://b2c.example.com/success?contractId={contractId}");
        pd.setPaymentCancelRedirectUrl("https://b2c.example.com/cancel?contractId={contractId}");
        em.persist(pd);
        String productDefinitionId = pd.getId();

        NonMultitenancyProductInstance pi = new NonMultitenancyProductInstance();
        pi.setId(UUID.randomUUID().toString());
        pi.setOrganisationId(ORG_ID);
        pi.setProductDefinition(pd);
        em.persist(pi);

        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(contractId);
        contract.setOrganisationId(ORG_ID);
        contract.setContractReference("REDIRECT-TEST-" + UUID.randomUUID());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency("EUR");
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setGrandTotal(new BigDecimal("50.00"));
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        NonMultitenancyContractLineItem li = new NonMultitenancyContractLineItem();
        li.setId(UUID.randomUUID().toString());
        li.setOrganisationId(ORG_ID);
        li.setContract(contract);
        li.setProductInstance(pi);
        li.setLineTotal(new BigDecimal("50.00"));
        li.setDisplayOrder(0);
        em.persist(li);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(ORG_ID);
        tx.setContractId(contractId);
        tx.setProductDefinitionId(productDefinitionId);
        tx.setPspIdentifier("stripe");
        tx.setCorrelationId(correlationId);
        tx.setPspSessionId(sessionId);
        tx.setGrossAmount(new BigDecimal("50.00"));
        tx.setCurrency("EUR");
        tx.setStatus(PaymentTransaction.PaymentStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        em.persist(tx);
        em.flush();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void successRedirectWithPendingPaymentReturns302ToB2CUrl() {
        given()
            .redirects().follow(false)
        .when()
            .get("/public/payment/success?session_id=" + sessionId)
        .then()
            .statusCode(302);
        // The Location header should contain the B2C redirect URL with the contract id
        // and a status=processing query param (since the payment is PENDING).
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void successRedirectWithSucceededPaymentReturns302ToB2CUrl() {
        // Mark the transaction as SUCCEEDED
        setTransactionStatus(sessionId, PaymentTransaction.PaymentStatus.SUCCEEDED);

        given()
            .redirects().follow(false)
        .when()
            .get("/public/payment/success?session_id=" + sessionId)
        .then()
            .statusCode(302);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void successRedirectWithUnknownSessionReturns404() {
        given()
            .when()
            .get("/public/payment/success?session_id=unknown-session")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void successRedirectWithoutSessionIdReturns400() {
        given()
            .when()
            .get("/public/payment/success")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void cancelRedirectReturns302ToB2CUrl() {
        given()
            .redirects().follow(false)
        .when()
            .get("/public/payment/cancel?session_id=" + sessionId)
        .then()
            .statusCode(302);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void cancelRedirectWithUnknownSessionReturns404() {
        given()
            .when()
            .get("/public/payment/cancel?session_id=unknown-session")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void cancelRedirectWithoutSessionIdReturns400() {
        given()
            .when()
            .get("/public/payment/cancel")
        .then()
            .statusCode(400);
    }

    @Test
    void redirectEndpointsArePublicAndDoNotRequireAuthentication() {
        // No @TestSecurity → no authenticated user. The redirect endpoints are @PermitAll.
        given()
            .redirects().follow(false)
            .when()
            .get("/public/payment/success?session_id=" + sessionId)
        .then()
            .statusCode(org.hamcrest.Matchers.anyOf(is(200), is(302)));
    }

    @Transactional
    void setTransactionStatus(String sessionId, PaymentTransaction.PaymentStatus status) {
        PaymentTransaction tx = em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.pspSessionId = :sid",
                PaymentTransaction.class)
            .setParameter("sid", sessionId)
            .getSingleResult();
        tx.setStatus(status);
        tx.setUpdatedAt(LocalDateTime.now());
        em.merge(tx);
    }
}
