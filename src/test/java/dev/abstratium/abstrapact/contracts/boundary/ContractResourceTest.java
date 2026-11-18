package dev.abstratium.abstrapact.contracts.boundary;

import dev.abstratium.abstrapact.contracts.boundary.dto.CreateDraftContractRequest;
import dev.abstratium.abstrapact.contracts.boundary.dto.LineItemRequest;
import dev.abstratium.abstrapact.contracts.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.abstrapact.product.boundary.dto.PartAttributeRequest;
import dev.abstratium.abstrapact.product.boundary.dto.PartRequest;
import dev.abstratium.abstrapact.product.boundary.dto.ProductDefinitionRequest;
import dev.abstratium.abstrapact.product.entity.PartAttributeDefinition;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ContractResourceTest {

    @Inject
    EntityManager em;

    @Inject
    UserTransaction utx;

    @Inject
    TestDataCleaner cleaner;

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    private String createProductDefinitionWithPart(String productCode, ProductDefinition.PaymentModel paymentModel) {
        ProductDefinitionRequest request = new ProductDefinitionRequest();
        request.setProductCode(productCode);
        request.setDescription("Test product for contract");
        request.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        request.setPaymentModel(paymentModel);
        request.setProductValidFrom(LocalDate.now());

        PartRequest part = new PartRequest();
        part.setPartCode("PART-" + productCode);
        part.setDescription("A part");
        part.setUnitPrice(new BigDecimal("100.00"));
        part.setDisplayOrder(1);

        PartAttributeRequest attr = new PartAttributeRequest();
        attr.setAttributeName("COLOR");
        attr.setDataType(PartAttributeDefinition.DataType.STRING);
        attr.setIsRequired(false);
        part.setAttributes(List.of(attr));

        request.setParts(List.of(part));

        return given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/product-definitions/complete")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateDraftContractWithLineItem() {
        String productDefId = createProductDefinitionWithPart("CONTRACT-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.PREPAID);

        PartInstanceAttributeRequest attrReq = new PartInstanceAttributeRequest();
        attrReq.setAttributeName("COLOR");
        attrReq.setAttributeValue("BLUE");

        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId(productDefId);
        lineItem.setDisplayOrder(1);
        lineItem.setAttributes(List.of(attrReq));

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("REF-" + System.currentTimeMillis());
        request.setPublicNotes("Draft for customer review");
        request.setLineItems(List.of(lineItem));

        String contractId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("contractReference", notNullValue())
            .body("state", equalTo("DRAFT"))
            .body("paymentModel", equalTo("PREPAID"))
            .body("currency", notNullValue())
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/contracts/" + contractId)
            .then()
            .statusCode(200)
            .body("id", equalTo(contractId))
            .body("state", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCalculatePostpaidContractPaymentModel() {
        String productDefId = createProductDefinitionWithPart("POSTPAID-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.POSTPAID);

        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId(productDefId);
        lineItem.setDisplayOrder(1);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("POSTPAID-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("paymentModel", equalTo("POSTPAID"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCreateDraftContractWithNoLineItems() {
        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("NO-LINE-ITEMS-REF-" + System.currentTimeMillis());

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("state", equalTo("DRAFT"))
            .body("paymentModel", equalTo("PREPAID"))
            .body("grandTotal", equalTo(0));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListContracts() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListContractsByState() {
        given()
            .when()
            .get("/api/contracts/state/DRAFT")
            .then()
            .statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForNonExistentContract() {
        given()
            .when()
            .get("/api/contracts/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn422WhenProductDefinitionNotFound() {
        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId("non-existent-product-def-id");
        lineItem.setDisplayOrder(1);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("422-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldDeleteDraftContract() {
        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("DELETE-REF-" + System.currentTimeMillis());

        String contractId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .when()
            .delete("/api/contracts/" + contractId)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/contracts/" + contractId)
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WhenDeletingNonExistentContract() {
        given()
            .when()
            .delete("/api/contracts/non-existent-id")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldCalculateGrandTotalFromLineItems() {
        String productDefId = createProductDefinitionWithPart("TOTAL-PROD-" + System.currentTimeMillis(), ProductDefinition.PaymentModel.PREPAID);

        LineItemRequest lineItem1 = new LineItemRequest();
        lineItem1.setProductDefinitionId(productDefId);
        lineItem1.setDisplayOrder(1);

        LineItemRequest lineItem2 = new LineItemRequest();
        lineItem2.setProductDefinitionId(productDefId);
        lineItem2.setDisplayOrder(2);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference("GRAND-TOTAL-REF-" + System.currentTimeMillis());
        request.setLineItems(List.of(lineItem1, lineItem2));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .body("grandTotal", equalTo(200.0f));
    }

    @Test
    void shouldRejectUnauthenticatedRequests() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"other-role"})
    void shouldRejectUnauthorizedRequests() {
        given()
            .when()
            .get("/api/contracts")
            .then()
            .statusCode(403);
    }

    // ==================== Seller view: state changes and payment attempts ====================

    private record CreatedContract(String contractId, String productDefinitionId) {}

    private CreatedContract createContractForSellerView(String ref) {
        String productDefId = createProductDefinitionWithPart("SELLER-PROD-" + System.currentTimeMillis(),
            ProductDefinition.PaymentModel.PREPAID);

        LineItemRequest lineItem = new LineItemRequest();
        lineItem.setProductDefinitionId(productDefId);
        lineItem.setDisplayOrder(1);

        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setContractReference(ref);
        request.setLineItems(List.of(lineItem));

        String contractId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/contracts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
        return new CreatedContract(contractId, productDefId);
    }

    private void insertStateChanges(String contractId) throws Exception {
        String processId = UUID.randomUUID().toString();
        String defaultOrgId = em.createQuery(
                "SELECT c.organisationId FROM Contract c WHERE c.id = :id", String.class)
            .setParameter("id", contractId)
            .getSingleResult();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_process_instance " +
                    "(id, organisation_id, contract_id, process_name, process_version, state) " +
                    "VALUES (:id, :orgId, :contractId, 'sales-process', '1.0', 'COMPLETED')")
                .setParameter("id", processId)
                .setParameter("orgId", defaultOrgId)
                .setParameter("contractId", contractId)
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_process_instance_step " +
                    "(id, organisation_id, process_instance_id, actor_user_id, step_timestamp, from_state, to_state, reason) " +
                    "VALUES (:id, :orgId, :processId, 'testuser', :now, :fromState, :toState, :reason)")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("orgId", defaultOrgId)
                .setParameter("processId", processId)
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .setParameter("fromState", "")
                .setParameter("toState", "DRAFT")
                .setParameter("reason", "created")
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_process_instance_step " +
                    "(id, organisation_id, process_instance_id, actor_user_id, step_timestamp, from_state, to_state, reason) " +
                    "VALUES (:id, :orgId, :processId, 'testuser', :now, :fromState, :toState, :reason)")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("orgId", defaultOrgId)
                .setParameter("processId", processId)
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusSeconds(1)))
                .setParameter("fromState", "DRAFT")
                .setParameter("toState", "OFFERED")
                .setParameter("reason", "offered")
                .executeUpdate();

            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }
    }

    private String insertPaymentAttempt(String contractId, String productDefinitionId) throws Exception {
        return insertPaymentAttempt(contractId, productDefinitionId, "PENDING");
    }

    private String insertPaymentAttempt(
            String contractId, String productDefinitionId, String status) throws Exception {
        String txId = UUID.randomUUID().toString();
        String defaultOrgId = em.createQuery(
                "SELECT c.organisationId FROM Contract c WHERE c.id = :id", String.class)
            .setParameter("id", contractId)
            .getSingleResult();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_payment_transaction " +
                    "(id, organisation_id, contract_id, product_definition_id, psp_identifier, " +
                    " correlation_id, gross_amount, currency, status, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :contractId, :productId, 'stripe', :correlationId, " +
                    " 100, 'EUR', :status, :now, :now)")
                .setParameter("id", txId)
                .setParameter("orgId", defaultOrgId)
                .setParameter("contractId", contractId)
                .setParameter("productId", productDefinitionId)
                .setParameter("correlationId", UUID.randomUUID().toString())
                .setParameter("status", status)
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }
        return txId;
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListStateChangesForContractAsSeller() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-STATE-" + System.currentTimeMillis());
        insertStateChanges(created.contractId());

        List<Map<String, Object>> changes = given()
            .when()
            .get("/api/contracts/" + created.contractId() + "/state-changes")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .extract()
            .jsonPath()
            .getList("$");

        assertEquals("DRAFT", changes.get(0).get("toState"));
        assertEquals("OFFERED", changes.get(1).get("toState"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListPaymentAttemptsForContractAsSeller() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-PAYMENTS-" + System.currentTimeMillis());
        insertPaymentAttempt(created.contractId(), created.productDefinitionId());

        List<Map<String, Object>> attempts = given()
            .when()
            .get("/api/contracts/" + created.contractId() + "/payment-attempts")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .extract()
            .jsonPath()
            .getList("$");

        assertEquals("PENDING", attempts.get(0).get("status"));
        assertNotNull(attempts.get(0).get("id"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldGetSinglePaymentAttemptForContractAsSeller() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-PAYMENT-GET-" + System.currentTimeMillis());
        String txId = insertPaymentAttempt(created.contractId(), created.productDefinitionId());

        given()
            .when()
            .get("/api/contracts/" + created.contractId() + "/payment-attempts/" + txId)
            .then()
            .statusCode(200)
            .body("id", equalTo(txId))
            .body("status", equalTo("PENDING"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404ForContractFromOtherOrganisation() throws Exception {
        String otherOrgId = "11111111-0000-0000-0000-000000000000";
        String contractId = UUID.randomUUID().toString();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_contract " +
                    "(id, organisation_id, contract_reference, contract_date, currency, " +
                    " grand_total, payment_model, state, public_notes, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :ref, :date, 'EUR', 0, 'PREPAID', 'DRAFT', 'notes', :now, :now)")
                .setParameter("id", contractId)
                .setParameter("orgId", otherOrgId)
                .setParameter("ref", "OTHER-ORG-" + System.currentTimeMillis())
                .setParameter("date", java.sql.Date.valueOf(LocalDate.now()))
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        given()
            .when()
            .get("/api/contracts/" + contractId + "/state-changes")
            .then()
            .statusCode(404);

        given()
            .when()
            .get("/api/contracts/" + contractId + "/payment-attempts")
            .then()
            .statusCode(404);
    }

    // ==================== Seller view: webhook events per payment attempt ====================

    private String insertWebhookEvent(
            String txId, String orgId, String pspEventId, String eventType,
            String processingResult, int receivedAtOffsetSeconds) throws Exception {
        String eventId = UUID.randomUUID().toString();
        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_webhook_event " +
                    "(id, organisation_id, psp_identifier, psp_event_id, event_type, " +
                    " correlation_id, payment_transaction_id, matched, processing_result, " +
                    " raw_payload, received_at) " +
                    "VALUES (:id, :orgId, 'stripe', :pspEventId, :eventType, :correlationId, " +
                    " :txId, true, :processingResult, '{}', :receivedAt)")
                .setParameter("id", eventId)
                .setParameter("orgId", orgId)
                .setParameter("pspEventId", pspEventId)
                .setParameter("eventType", eventType)
                .setParameter("correlationId", UUID.randomUUID().toString())
                .setParameter("txId", txId)
                .setParameter("processingResult", processingResult)
                .setParameter("receivedAt", java.sql.Timestamp.valueOf(
                    java.time.LocalDateTime.now().plusSeconds(receivedAtOffsetSeconds)))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }
        return eventId;
    }

    private String orgIdOfContract(String contractId) {
        return em.createQuery(
                "SELECT c.organisationId FROM Contract c WHERE c.id = :id", String.class)
            .setParameter("id", contractId)
            .getSingleResult();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldListWebhookEventsForPaymentAttemptAsSeller() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-WH-" + System.currentTimeMillis());
        String txId = insertPaymentAttempt(created.contractId(), created.productDefinitionId());
        // Only one PENDING transaction per contract is allowed, so the second
        // attempt uses a terminal status.
        String otherTxId = insertPaymentAttempt(
            created.contractId(), created.productDefinitionId(), "FAILED");
        String orgId = orgIdOfContract(created.contractId());
        String otherOrgId = "11111111-0000-0000-0000-000000000000";

        // Two events for the transaction under test (inserted out of order to
        // verify receivedAt ASC ordering).
        insertWebhookEvent(txId, orgId, "evt_wh_2_" + System.nanoTime(),
            "charge.updated", "PROCESSED", 2);
        insertWebhookEvent(txId, orgId, "evt_wh_1_" + System.nanoTime(),
            "checkout.session.completed", "PROCESSED", 1);
        // An event for a different transaction of the same contract — must not appear.
        insertWebhookEvent(otherTxId, orgId, "evt_other_tx_" + System.nanoTime(),
            "checkout.session.expired", "PROCESSED", 3);
        // An event referencing this transaction but stored under a different
        // org — must never leak across organisations.
        insertWebhookEvent(txId, otherOrgId, "evt_other_org_" + System.nanoTime(),
            "checkout.session.completed", "PROCESSED", 4);

        List<Map<String, Object>> events = given()
            .when()
            .get("/api/contracts/" + created.contractId()
                + "/payment-attempts/" + txId + "/webhook-events")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .extract()
            .jsonPath()
            .getList("$");

        assertEquals("checkout.session.completed", events.get(0).get("eventType"));
        assertEquals("charge.updated", events.get(1).get("eventType"));
        assertEquals("PROCESSED", events.get(0).get("processingResult"));
        assertNotNull(events.get(0).get("id"));
        assertNotNull(events.get(0).get("receivedAt"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturnEmptyWebhookEventsWhenNoneRecorded() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-WH-EMPTY-" + System.currentTimeMillis());
        String txId = insertPaymentAttempt(created.contractId(), created.productDefinitionId());

        given()
            .when()
            .get("/api/contracts/" + created.contractId()
                + "/payment-attempts/" + txId + "/webhook-events")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WebhookEventsForContractFromOtherOrganisation() throws Exception {
        String otherOrgId = "11111111-0000-0000-0000-000000000000";
        String contractId = UUID.randomUUID().toString();
        String txId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_product_definition " +
                    "(id, organisation_id, product_code, description, billing_model, " +
                    " product_valid_from, cross_tenant_api_allowed) " +
                    "VALUES (:id, :orgId, :code, 'Product in other org', 'FIXED_PRICE', :validFrom, true)")
                .setParameter("id", productId)
                .setParameter("orgId", otherOrgId)
                .setParameter("code", "OTHER-ORG-PROD-" + System.currentTimeMillis())
                .setParameter("validFrom", java.sql.Date.valueOf(LocalDate.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_contract " +
                    "(id, organisation_id, contract_reference, contract_date, currency, " +
                    " grand_total, payment_model, state, public_notes, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :ref, :date, 'EUR', 0, 'PREPAID', 'DRAFT', 'notes', :now, :now)")
                .setParameter("id", contractId)
                .setParameter("orgId", otherOrgId)
                .setParameter("ref", "OTHER-ORG-WH-" + System.currentTimeMillis())
                .setParameter("date", java.sql.Date.valueOf(LocalDate.now()))
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_payment_transaction " +
                    "(id, organisation_id, contract_id, product_definition_id, psp_identifier, " +
                    " correlation_id, gross_amount, currency, status, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :contractId, :productId, 'stripe', :correlationId, " +
                    " 100, 'EUR', 'SUCCEEDED', :now, :now)")
                .setParameter("id", txId)
                .setParameter("orgId", otherOrgId)
                .setParameter("contractId", contractId)
                .setParameter("productId", productId)
                .setParameter("correlationId", UUID.randomUUID().toString())
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        insertWebhookEvent(txId, otherOrgId, "evt_foreign_" + System.nanoTime(),
            "checkout.session.completed", "PROCESSED", 0);

        given()
            .when()
            .get("/api/contracts/" + contractId + "/payment-attempts/" + txId + "/webhook-events")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WebhookEventsForAttemptOfOtherContract() throws Exception {
        CreatedContract created = createContractForSellerView("SELLER-WH-A-" + System.currentTimeMillis());
        CreatedContract other = createContractForSellerView("SELLER-WH-B-" + System.currentTimeMillis());
        String otherTxId = insertPaymentAttempt(other.contractId(), other.productDefinitionId());

        given()
            .when()
            .get("/api/contracts/" + created.contractId()
                + "/payment-attempts/" + otherTxId + "/webhook-events")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void shouldReturn404WebhookEventsForCustomerOfContractInOtherOrganisation() throws Exception {
        // A customer purchased from a different seller org: the contract, the
        // CUSTOMER account link (to "testuser"), the payment transaction, and
        // the webhook event all live in OTHER_ORG_ID. The org-scoped endpoint
        // must still deny access — being the paying customer does not grant
        // visibility into another organisation's contract.
        String otherOrgId = "11111111-0000-0000-0000-000000000000";
        String contractId = UUID.randomUUID().toString();
        String txId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        utx.begin();
        try {
            em.createNativeQuery(
                    "INSERT INTO T_product_definition " +
                    "(id, organisation_id, product_code, description, billing_model, " +
                    " product_valid_from, cross_tenant_api_allowed) " +
                    "VALUES (:id, :orgId, :code, 'Product in other org', 'FIXED_PRICE', :validFrom, true)")
                .setParameter("id", productId)
                .setParameter("orgId", otherOrgId)
                .setParameter("code", "OTHER-ORG-CUST-PROD-" + System.currentTimeMillis())
                .setParameter("validFrom", java.sql.Date.valueOf(LocalDate.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_contract " +
                    "(id, organisation_id, contract_reference, contract_date, currency, " +
                    " grand_total, payment_model, state, public_notes, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :ref, :date, 'EUR', 0, 'PREPAID', 'DRAFT', 'notes', :now, :now)")
                .setParameter("id", contractId)
                .setParameter("orgId", otherOrgId)
                .setParameter("ref", "OTHER-ORG-CUST-" + System.currentTimeMillis())
                .setParameter("date", java.sql.Date.valueOf(LocalDate.now()))
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_contract_account_role " +
                    "(id, organisation_id, contract_id, account_id, role_type) " +
                    "VALUES (:id, :orgId, :contractId, 'testuser', 'CUSTOMER')")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("orgId", otherOrgId)
                .setParameter("contractId", contractId)
                .executeUpdate();

            em.createNativeQuery(
                    "INSERT INTO T_payment_transaction " +
                    "(id, organisation_id, contract_id, product_definition_id, psp_identifier, " +
                    " correlation_id, gross_amount, currency, status, created_at, updated_at) " +
                    "VALUES (:id, :orgId, :contractId, :productId, 'stripe', :correlationId, " +
                    " 100, 'EUR', 'SUCCEEDED', :now, :now)")
                .setParameter("id", txId)
                .setParameter("orgId", otherOrgId)
                .setParameter("contractId", contractId)
                .setParameter("productId", productId)
                .setParameter("correlationId", UUID.randomUUID().toString())
                .setParameter("now", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()))
                .executeUpdate();
            utx.commit();
        } catch (Exception e) {
            utx.rollback();
            throw e;
        }

        insertWebhookEvent(txId, otherOrgId, "evt_cust_foreign_" + System.nanoTime(),
            "checkout.session.completed", "PROCESSED", 0);

        given()
            .when()
            .get("/api/contracts/" + contractId + "/payment-attempts/" + txId + "/webhook-events")
            .then()
            .statusCode(404);
    }

    @Test
    void shouldRequireAuthenticationForWebhookEventsEndpoint() {
        given()
            .when()
            .get("/api/contracts/" + UUID.randomUUID()
                + "/payment-attempts/" + UUID.randomUUID() + "/webhook-events")
            .then()
            .statusCode(anyOf(is(400), is(401)));
    }
}
