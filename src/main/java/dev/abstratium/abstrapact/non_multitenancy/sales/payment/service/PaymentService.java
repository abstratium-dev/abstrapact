package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContractLineItem;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentEventResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction.PaymentStatus;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent.ProcessingResult;
import dev.abstratium.abstrapact.non_multitenancy.sales.service.SalesProcessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates payment creation, webhook result handling, and the staleness check.
 *
 * <p>See {@code docs/DESIGN_OF_PAYMENT.md}.
 */
@ApplicationScoped
public class PaymentService {

    /** System actor id recorded on contract state transitions triggered by webhooks. */
    public static final String SYSTEM_ACTOR = "system";

    /** How long to wait for a racing winner's PSP session to appear on a PENDING tx. */
    private static final Duration PENDING_SESSION_WAIT = Duration.ofSeconds(30);

    /** Poll interval while waiting for the winner's PSP session. */
    private static final Duration PENDING_SESSION_POLL = Duration.ofMillis(100);

    @Inject
    EntityManager em;

    @Inject
    PSPSelector pspSelector;

    @Inject
    PaymentTransactionService transactionService;

    @Inject
    WebhookEventService webhookEventService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Lazy injection to break the circular dependency:
     * {@code SalesProcessService} → {@code PaymentService} → {@code SalesProcessService}.
     */
    @Inject
    Instance<SalesProcessService> salesProcessService;

    @ConfigProperty(name = "abstrapact.payment.stripe.success-url")
    String successUrl;

    @ConfigProperty(name = "abstrapact.payment.stripe.cancel-url")
    String cancelUrl;

    @ConfigProperty(name = "abstrapact.payment.webhook.stale-after-hours",
        defaultValue = "24")
    long staleAfterHours;

    @ConfigProperty(name = "abstrapact.payment.webhook.max-payload-size-bytes",
        defaultValue = "131072")
    int maxPayloadSizeBytes;

    // ==================== Payment creation ====================

    /**
     * Creates a payment (Stripe Checkout Session) for a prepaid contract.
     *
     * <p>Runs in two phases so the PSP call happens outside the database
     * transaction that records the payment attempt:
     *
     * <ol>
     *   <li>Persist a {@code PENDING} {@link PaymentTransaction} and commit it
     *       immediately ({@code REQUIRES_NEW}). A concurrent duplicate insert fails
     *       on {@code UQ_payment_transaction_pending_contract} <em>before</em> any
     *       Stripe call is made, so no orphaned Checkout Session is created.</li>
     *   <li>Call the PSP to create the checkout session, then store the session id
     *       and checkout URL in a second {@code REQUIRES_NEW} transaction.</li>
     * </ol>
     *
     * @param contractId      the contract to create a payment for
     * @param actorAccountId  the caller's account id (used for the contract access check)
     * @return the PSP response containing the checkout URL and session id
     */
    @Transactional
    public CreatePaymentResponse createPaymentForContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);
        return createPayment(contract);
    }

    /**
     * Starts a new payment attempt for a contract that is {@code AWAITING_PAYMENT}.
     *
     * <p>If a {@code PENDING} transaction with a live session exists, its checkout
     * URL is returned unchanged. If the previous attempt is {@code EXPIRED} or
     * {@code FAILED}, a fresh {@code PENDING} transaction and Stripe session are
     * created (the {@code UQ_payment_transaction_pending_contract} constraint
     * permits a new {@code PENDING} row once the old one is terminal).
     *
     * @return the checkout URL for the (re-)created session
     */
    @Transactional
    public String retryPayment(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);
        if (contract.getState() != ContractState.AWAITING_PAYMENT) {
            throw new WebApplicationException(
                Response.status(422)
                    .entity("Contract must be in AWAITING_PAYMENT state to retry payment, but is: "
                        + contract.getState())
                    .build());
        }
        return createPayment(contract).getCheckoutUrl();
    }

    private CreatePaymentResponse createPayment(NonMultitenancyContract contract) {
        NonMultitenancyProductDefinition productDef = resolveProductDefinition(contract);

        if (productDef.getStripeSecretKey() == null || productDef.getStripeSecretKey().isBlank()) {
            throw new WebApplicationException(
                Response.status(422)
                    .entity("Product definition has no Stripe secret key configured: "
                        + productDef.getProductCode())
                    .build());
        }

        // State-based idempotency guard: if a PENDING transaction already exists for
        // this contract, reuse it instead of creating a duplicate.
        PaymentTransaction existing = findPendingTransactionForContract(contract.getId());
        if (existing != null) {
            if (existing.getPspSessionId() != null) {
                // The Stripe session was already created — just return the checkout URL.
                return new CreatePaymentResponse(
                    resolveCheckoutUrl(existing),
                    existing.getPspSessionId());
            }
            // Transaction exists but no Stripe session yet (first attempt died before
            // calling Stripe). Fall through to create the Stripe session.
            return createStripeSessionAndStore(existing, productDef.getStripeSecretKey());
        }

        String correlationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setOrganisationId(contract.getOrganisationId());
        tx.setContractId(contract.getId());
        tx.setProductDefinitionId(productDef.getId());
        tx.setPspIdentifier(pspSelector.getActive().getPspIdentifier());
        tx.setCorrelationId(correlationId);
        tx.setGrossAmount(contract.getGrandTotal());
        tx.setCurrency(contract.getCurrency());
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        // Committed in its own transaction BEFORE the Stripe call. A concurrent
        // insert fails here on UQ_payment_transaction_pending_contract — before an
        // orphaned Stripe session could be created.
        transactionService.persistNewPending(tx);

        return createStripeSessionAndStore(tx, productDef.getStripeSecretKey());
    }

    // ==================== helpers: state-based idempotency ====================

    /**
     * Returns the checkout URL of the existing PENDING transaction for the
     * contract, or {@code null} if none exists.
     *
     * <p>Used by the REST layer to recover when a concurrent {@code accept} or
     * {@code retry-payment} with a different idempotency key lost the race on
     * {@code UQ_payment_transaction_pending_contract}: the losing transaction
     * rolled back, but the client can still receive the winner's checkout URL.
     * Must run in a fresh transaction after the loser has rolled back.
     *
     * <p>Because the winner commits its {@code PENDING} row before calling Stripe,
     * the losing request may observe the row before {@code psp_session_id} is
     * stored. This method polls briefly for the winner's session to appear; it
     * returns {@code null} if the winner's PSP call never completes.
     */
    @Transactional
    public String findPendingCheckoutUrlForContract(String contractId) {
        long deadline = System.nanoTime() + PENDING_SESSION_WAIT.toNanos();
        while (true) {
            PaymentTransaction existing = findPendingTransactionForContract(contractId);
            if (existing == null) {
                return null;
            }
            if (existing.getPspSessionId() != null) {
                return resolveCheckoutUrl(existing);
            }
            if (System.nanoTime() > deadline) {
                return null;
            }
            // Detach so the next poll re-reads the row instead of returning the
            // stale persistence-context copy.
            em.clear();
            try {
                Thread.sleep(PENDING_SESSION_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private PaymentTransaction findPendingTransactionForContract(String contractId) {
        List<PaymentTransaction> results = em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.contractId = :cid AND t.status = :status",
                PaymentTransaction.class)
            .setParameter("cid", contractId)
            .setParameter("status", PaymentStatus.PENDING)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private String resolveCheckoutUrl(PaymentTransaction tx) {
        if (tx.getCheckoutUrl() != null) {
            return tx.getCheckoutUrl();
        }
        // Fallback: reconstruct from Stripe session. This should not happen
        // if the checkout URL was stored properly on creation.
        return successUrl.replace("{CHECKOUT_SESSION_ID}", tx.getPspSessionId());
    }

    private CreatePaymentResponse createStripeSessionAndStore(
            PaymentTransaction tx, String stripeSecretKey) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setContractId(tx.getContractId());
        request.setCorrelationId(tx.getCorrelationId());
        request.setAmount(tx.getGrossAmount());
        request.setCurrency(tx.getCurrency());
        request.setDescription("Contract " + tx.getContractId());
        request.setSuccessUrl(successUrl);
        request.setCancelUrl(cancelUrl);
        request.setStripeSecretKey(stripeSecretKey);

        CreatePaymentResponse response = pspSelector.getActive().createPayment(request);

        // Stored in its own transaction (REQUIRES_NEW) so the session data survives
        // even if the caller's transaction later rolls back.
        transactionService.storeSession(tx.getId(), response.getPspSessionId(),
            response.getCheckoutUrl());

        return response;
    }

    // ==================== Webhook result handling ====================

    /**
     * Processes a verified {@link PaymentEventResult}:
     *
     * <ol>
     *   <li>Persist the {@link WebhookEvent} row (deduplicated by
     *       {@code (psp_identifier, psp_event_id)}).</li>
     *   <li>If duplicate → record {@code DUPLICATE}, no state change.</li>
     *   <li>If no matching transaction → record {@code UNMATCHED}, no state change.</li>
     *   <li>If transaction terminal → record {@code DUPLICATE}, no state change.</li>
     *   <li>If event type not handled → record {@code IGNORED}, no state change.</li>
     *   <li>If success + stale → mark transaction {@code STALE}, record {@code STALE},
     *       no contract transition.</li>
     *   <li>If success + fresh → mark transaction {@code SUCCEEDED}, store fee + ref,
     *       transition contract to {@code RUNNING}, record {@code PROCESSED}.</li>
     *   <li>If failure → mark transaction {@code FAILED}, record {@code PROCESSED},
     *       contract stays {@code AWAITING_PAYMENT}.</li>
     * </ol>
     */
    @Transactional
    public void handlePaymentResult(PaymentEventResult result) {
        // 1. Check for duplicate event first (before any state changes).
        if (webhookEventService.existsByPspEventId(pspSelector.getActive().getPspIdentifier(), result.getPspEventId())) {
            // Duplicate event — no state change, no new webhook event row.
            return;
        }

        // 2. Determine the processing result and apply state changes.
        ProcessingOutcome outcome = determineOutcome(result);

        // 3. Persist the webhook event row with the final processing result.
        WebhookEvent event = toWebhookEvent(result, outcome.processingResult(), pspSelector.getActive().getPspIdentifier());
        if (outcome.matchedTransaction() != null) {
            event.setMatched(true);
            event.setPaymentTransactionId(outcome.matchedTransaction().getId());
            event.setOrganisationId(outcome.matchedTransaction().getOrganisationId());
        }
        webhookEventService.persistOrFindDuplicate(event);

        // 4. Apply transaction state changes by loading and updating the managed entity.
        if (outcome.updatedTransaction() != null) {
            PaymentTransaction managed = em.find(PaymentTransaction.class,
                outcome.updatedTransaction().getId());
            if (managed != null) {
                if (outcome.updatedTransaction().getStatus() != null) {
                    managed.setStatus(outcome.updatedTransaction().getStatus());
                }
                managed.setUpdatedAt(outcome.updatedTransaction().getUpdatedAt());
                if (outcome.updatedTransaction().getFeeAmount() != null) {
                    managed.setFeeAmount(outcome.updatedTransaction().getFeeAmount());
                    managed.setNetAmount(outcome.updatedTransaction().getNetAmount());
                }
                if (outcome.updatedTransaction().getPspTransactionRef() != null) {
                    managed.setPspTransactionRef(outcome.updatedTransaction().getPspTransactionRef());
                }
            }
        }

        // 5. Transition the contract to RUNNING for successful fresh payments.
        if (outcome.transitionToRunning()) {
            salesProcessService.get().transitionToRunning(
                outcome.matchedTransaction().getContractId(), SYSTEM_ACTOR);
        }
    }

    // ==================== Rejected webhook recording ====================

    /**
     * Records a rejected webhook event (signature failure, no matching product, malformed
     * payload) in {@code T_webhook_event} with {@code processing_result=REJECTED}.
     *
     * <p>This is a best-effort audit record: the payload is untrusted (signature was not
     * verified), so the extracted event id, type, and correlation id may be null or
     * fabricated. The {@code rejectionReason} parameter explains why the webhook was
     * rejected.
     *
     * <p>The caller is responsible for checking the payload size limit before calling this
     * method — oversized payloads should not be recorded (only logged).
     *
     * @param payload         the raw webhook payload (untrusted)
     * @param rejectionReason human-readable explanation of why the webhook was rejected
     */
    @Transactional
    public void recordRejectedEvent(String payload, String rejectionReason) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID().toString());
        event.setPspIdentifier(pspSelector.getActive().getPspIdentifier());
        event.setMatched(false);
        event.setProcessingResult(ProcessingResult.REJECTED);
        event.setRawPayload(payload);
        event.setReceivedAt(LocalDateTime.now());
        event.setRejectionReason(rejectionReason);

        // Best-effort extraction of event info from the untrusted payload.
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode idNode = root.path("id");
            if (idNode.isTextual()) {
                event.setPspEventId(idNode.asText());
            }
            JsonNode typeNode = root.path("type");
            if (typeNode.isTextual()) {
                event.setEventType(typeNode.asText());
            }
            JsonNode metadata = root.path("data").path("object").path("metadata");
            if (metadata.isObject() && metadata.has("correlation_id")) {
                String corrId = metadata.get("correlation_id").asText(null);
                if (corrId != null && !corrId.isBlank()) {
                    event.setCorrelationId(corrId);
                }
            }
        } catch (Exception e) {
            // Malformed JSON — leave event id, type, and correlation id as null.
        }

        webhookEventService.persistOrFindDuplicate(event);
    }

    /**
     * Returns the configured maximum payload size in bytes. Webhooks exceeding this limit
     * are not recorded in {@code T_webhook_event} — they are logged via the JBoss logger
     * only.
     */
    public int getMaxPayloadSizeBytes() {
        return maxPayloadSizeBytes;
    }

    /**
     * Determines the processing outcome for a verified webhook event without modifying any
     * state. The caller persists the webhook event and applies the state changes.
     */
    private ProcessingOutcome determineOutcome(PaymentEventResult result) {
        // No correlation id → unmatched.
        if (result.getCorrelationId() == null) {
            return new ProcessingOutcome(ProcessingResult.UNMATCHED, null, null, false);
        }

        Optional<PaymentTransaction> txOpt =
            transactionService.findByCorrelationId(result.getCorrelationId());
        if (txOpt.isEmpty()) {
            return new ProcessingOutcome(ProcessingResult.UNMATCHED, null, null, false);
        }

        PaymentTransaction tx = txOpt.get();

        // Fee-only update on a terminal transaction — update fee/net without state transition.
        // Stripe sends fee data in charge.updated, which may arrive after the transaction is
        // already SUCCEEDED. We still want to record the fee.
        if ("charge.updated".equals(result.getEventType())
                && result.getFeeAmount() != null
                && (tx.getStatus() == PaymentStatus.SUCCEEDED
                    || tx.getStatus() == PaymentStatus.STALE)) {
            PaymentTransaction updated = new PaymentTransaction();
            updated.setId(tx.getId());
            updated.setFeeAmount(result.getFeeAmount());
            updated.setNetAmount(tx.getGrossAmount().subtract(result.getFeeAmount()));
            if (result.getPspTransactionRef() != null) {
                updated.setPspTransactionRef(result.getPspTransactionRef());
            }
            updated.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, updated, false);
        }

        // Success arriving for a transaction we already marked EXPIRED (e.g. the
        // customer paid on a session that Stripe had not yet expired, or a late
        // success raced our expiry sweep): the payment is real and must not be
        // dropped. Record it as STALE for manual review — same semantics as a
        // success arriving after the staleness window.
        if (tx.getStatus() == PaymentStatus.EXPIRED
                && result.getStatus() == PaymentStatus.SUCCEEDED) {
            PaymentTransaction stale = new PaymentTransaction();
            stale.setId(tx.getId());
            stale.setStatus(PaymentStatus.STALE);
            if (result.getFeeAmount() != null) {
                stale.setFeeAmount(result.getFeeAmount());
                stale.setNetAmount(tx.getGrossAmount().subtract(result.getFeeAmount()));
            }
            if (result.getPspTransactionRef() != null) {
                stale.setPspTransactionRef(result.getPspTransactionRef());
            }
            stale.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.STALE, tx, stale, false);
        }

        // Terminal state → duplicate.
        if (tx.getStatus() == PaymentStatus.SUCCEEDED
                || tx.getStatus() == PaymentStatus.FAILED
                || tx.getStatus() == PaymentStatus.STALE
                || tx.getStatus() == PaymentStatus.EXPIRED) {
            return new ProcessingOutcome(ProcessingResult.DUPLICATE, tx, null, false);
        }

        // Event type not actively processed → IGNORED.
        if (!isHandledEventType(result.getEventType())) {
            return new ProcessingOutcome(ProcessingResult.IGNORED, tx, null, false);
        }

        // Success path.
        if (result.getStatus() == PaymentStatus.SUCCEEDED) {
            if (isStale(tx)) {
                PaymentTransaction stale = new PaymentTransaction();
                stale.setId(tx.getId());
                stale.setStatus(PaymentStatus.STALE);
                stale.setUpdatedAt(LocalDateTime.now());
                return new ProcessingOutcome(ProcessingResult.STALE, tx, stale, false);
            }
            PaymentTransaction updated = new PaymentTransaction();
            updated.setId(tx.getId());
            updated.setStatus(PaymentStatus.SUCCEEDED);
            if (result.getFeeAmount() != null) {
                updated.setFeeAmount(result.getFeeAmount());
                updated.setNetAmount(tx.getGrossAmount().subtract(result.getFeeAmount()));
            }
            if (result.getPspTransactionRef() != null) {
                updated.setPspTransactionRef(result.getPspTransactionRef());
            }
            updated.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, updated, true);
        }

        // Session-expired path. The checkout session died unpaid — mark the
        // transaction EXPIRED so a fresh payment attempt can be created via
        // POST /api/public/sales/contracts/{id}/retry-payment.
        if (result.getStatus() == PaymentStatus.EXPIRED) {
            PaymentTransaction expired = new PaymentTransaction();
            expired.setId(tx.getId());
            expired.setStatus(PaymentStatus.EXPIRED);
            expired.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, expired, false);
        }

        // Failure path.
        if (result.getStatus() == PaymentStatus.FAILED) {
            PaymentTransaction failed = new PaymentTransaction();
            failed.setId(tx.getId());
            failed.setStatus(PaymentStatus.FAILED);
            failed.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, failed, false);
        }

        // PENDING result on a handled event type — update fee if present, but no state transition.
        if (result.getFeeAmount() != null) {
            PaymentTransaction updated = new PaymentTransaction();
            updated.setId(tx.getId());
            updated.setFeeAmount(result.getFeeAmount());
            updated.setNetAmount(tx.getGrossAmount().subtract(result.getFeeAmount()));
            if (result.getPspTransactionRef() != null) {
                updated.setPspTransactionRef(result.getPspTransactionRef());
            }
            updated.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, updated, false);
        }
        return new ProcessingOutcome(ProcessingResult.IGNORED, tx, null, false);
    }

    /**
     * Internal record capturing the outcome of processing a webhook event.
     *
     * @param processingResult     the result to record on the WebhookEvent
     * @param matchedTransaction   the matched PaymentTransaction (null if unmatched)
     * @param updatedTransaction   the updated PaymentTransaction to merge (null if no update)
     * @param transitionToRunning  whether to transition the contract to RUNNING
     */
    private record ProcessingOutcome(
            ProcessingResult processingResult,
            PaymentTransaction matchedTransaction,
            PaymentTransaction updatedTransaction,
            boolean transitionToRunning) {
    }

    // ==================== Redirect lookup ====================

    /**
     * Finds a payment transaction by PSP session id (used by the success/cancel redirect
     * endpoints). Loads the contract and product definition for redirect URL resolution.
     */
    public Optional<PaymentTransaction> findPaymentBySessionId(String sessionId) {
        return transactionService.findByPspSessionId(sessionId);
    }

    /**
     * Resolves the product definition for a contract — used by the redirect endpoints to
     * find the per-product B2C redirect URLs. Goes directly via the payment transaction's
     * stored {@code productDefinitionId} rather than joining through contract line items.
     */
    public Optional<NonMultitenancyProductDefinition> resolveProductDefinitionForContract(
            String contractId) {
        return em.createQuery(
                "SELECT pd FROM NonMultitenancyProductDefinition pd " +
                "WHERE pd.id IN (" +
                "  SELECT t.productDefinitionId FROM PaymentTransaction t " +
                "  WHERE t.contractId = :cid" +
                ")",
                NonMultitenancyProductDefinition.class)
            .setParameter("cid", contractId)
            .setMaxResults(1)
            .getResultStream()
            .findFirst();
    }

    /**
     * Loads the contract for a payment transaction (used by the redirect endpoints).
     */
    public Optional<NonMultitenancyContract> findContractById(String contractId) {
        return Optional.ofNullable(em.find(NonMultitenancyContract.class, contractId));
    }

    // ==================== helpers ====================

    private NonMultitenancyContract loadContractForAccount(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        if (contract == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Contract not found: " + contractId)
                    .build());
        }
        boolean linked = em.createQuery(
                "SELECT COUNT(r) FROM NonMultitenancyContractAccountRole r " +
                "WHERE r.contract.id = :cid AND r.accountId = :aid AND r.roleType = 'CUSTOMER'",
                Long.class)
            .setParameter("cid", contractId)
            .setParameter("aid", actorAccountId)
            .getSingleResult() > 0;
        if (!linked) {
            throw new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                    .entity("Contract not accessible for this account")
                    .build());
        }
        return contract;
    }

    private NonMultitenancyProductDefinition resolveProductDefinition(NonMultitenancyContract contract) {
        List<NonMultitenancyContractLineItem> lineItems = contract.getLineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            throw new WebApplicationException(
                Response.status(422)
                    .entity("Contract has no line items: " + contract.getId())
                    .build());
        }
        // First line item's product definition provides the Stripe credentials.
        NonMultitenancyContractLineItem first = lineItems.get(0);
        return em.find(NonMultitenancyProductDefinition.class,
            first.getProductInstance().getProductDefinition().getId());
    }

    private boolean isStale(PaymentTransaction tx) {
        if (tx.getCreatedAt() == null) {
            return false;
        }
        Duration age = Duration.between(tx.getCreatedAt(), LocalDateTime.now());
        return age.toHours() >= staleAfterHours;
    }

    private static boolean isHandledEventType(String eventType) {
        return eventType != null && switch (eventType) {
            case "checkout.session.completed",
                 "checkout.session.async_payment_succeeded",
                 "checkout.session.async_payment_failed",
                 "checkout.session.expired",
                 "payment_intent.succeeded",
                 "charge.updated" -> true; // fee update; may arrive after SUCCEEDED
            default -> false;
        };
    }

    private static WebhookEvent toWebhookEvent(PaymentEventResult result, ProcessingResult processingResult,
                                               String pspIdentifier) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID().toString());
        event.setPspIdentifier(pspIdentifier);
        event.setPspEventId(result.getPspEventId());
        event.setEventType(result.getEventType());
        event.setCorrelationId(result.getCorrelationId());
        event.setMatched(result.isMatched());
        event.setRawPayload(result.getRawPayload());
        event.setReceivedAt(LocalDateTime.now());
        event.setProcessingResult(processingResult);
        return event;
    }

}
