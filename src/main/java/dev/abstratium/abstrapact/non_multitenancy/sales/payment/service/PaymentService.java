package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

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

    @Inject
    EntityManager em;

    @Inject
    PSPSelector pspSelector;

    @Inject
    PaymentTransactionService transactionService;

    @Inject
    WebhookEventService webhookEventService;

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

    // ==================== Payment creation ====================

    /**
     * Creates a payment (Stripe Checkout Session) for a prepaid contract.
     *
     * <p>Loads the contract, resolves the product definition from the first line item,
     * generates a correlation id, persists a {@code PENDING} {@link PaymentTransaction},
     * calls the active PSP, and stores the PSP session id on the transaction.
     *
     * @param contractId      the contract to create a payment for
     * @param actorAccountId  the caller's account id (used for the contract access check)
     * @return the PSP response containing the checkout URL and session id
     */
    @Transactional
    public CreatePaymentResponse createPaymentForContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);
        NonMultitenancyProductDefinition productDef = resolveProductDefinition(contract);

        if (productDef.getStripeSecretKey() == null || productDef.getStripeSecretKey().isBlank()) {
            throw new WebApplicationException(
                Response.status(422)
                    .entity("Product definition has no Stripe secret key configured: "
                        + productDef.getProductCode())
                    .build());
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
        transactionService.persist(tx);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setContractId(contract.getId());
        request.setCorrelationId(correlationId);
        request.setAmount(contract.getGrandTotal());
        request.setCurrency(contract.getCurrency());
        request.setDescription("Contract " + contract.getContractReference());
        request.setSuccessUrl(successUrl);
        request.setCancelUrl(cancelUrl);
        request.setStripeSecretKey(productDef.getStripeSecretKey());

        CreatePaymentResponse response = pspSelector.getActive().createPayment(request);

        tx.setPspSessionId(response.getPspSessionId());
        em.merge(tx);

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
                managed.setStatus(outcome.updatedTransaction().getStatus());
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

        // Terminal state → duplicate.
        if (tx.getStatus() == PaymentStatus.SUCCEEDED
                || tx.getStatus() == PaymentStatus.FAILED
                || tx.getStatus() == PaymentStatus.STALE) {
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

        // Failure path.
        if (result.getStatus() == PaymentStatus.FAILED) {
            PaymentTransaction failed = new PaymentTransaction();
            failed.setId(tx.getId());
            failed.setStatus(PaymentStatus.FAILED);
            failed.setUpdatedAt(LocalDateTime.now());
            return new ProcessingOutcome(ProcessingResult.PROCESSED, tx, failed, false);
        }

        // PENDING result on a handled event type — nothing to do yet.
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
                 "payment_intent.succeeded" -> true;
            case "charge.updated" -> false; // fee-only update, no state transition
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
