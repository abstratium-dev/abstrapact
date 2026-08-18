package dev.abstratium.abstrapact.non_multitenancy.sales.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.CreatePaymentResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentService;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.UnsupportedPaymentModelException;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceState;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstance;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProcessInstanceStep;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manages state transitions for the sales process.
 *
 * Each contract has an associated {@link NonMultitenancyProcessInstance} with
 * {@code processName} {@code "sales-process"} and {@code contractId} set to the contract's id.
 */
@ApplicationScoped
public class SalesProcessService {

    private static final String PROCESS_NAME = "sales-process";
    private static final String PROCESS_VERSION = "1.0";

    @Inject
    EntityManager em;

    /**
     * Lazy injection to break the circular dependency:
     * {@code SalesProcessService} → {@code PaymentService} → {@code SalesProcessService}.
     */
    @Inject
    Instance<PaymentService> paymentService;

    /**
     * Creates a new process instance for the given contract and records the initial step.
     * Must be called inside an active {@code @Transactional} boundary.
     *
     * @param contract the newly created contract (state must be {@code DRAFT})
     * @param actorAccountId the caller's account id
     * @return the created process instance
     */
    @Transactional
    public NonMultitenancyProcessInstance startSalesProcess(
            NonMultitenancyContract contract,
            String actorAccountId) {

        NonMultitenancyProcessInstance process = new NonMultitenancyProcessInstance();
        process.setId(UUID.randomUUID().toString());
        process.setOrganisationId(contract.getOrganisationId());
        process.setContractId(contract.getId());
        process.setProcessName(PROCESS_NAME);
        process.setProcessVersion(PROCESS_VERSION);
        process.setState(ProcessInstanceState.IN_PROGRESS);
        em.persist(process);

        recordStep(process, null, ContractState.DRAFT.name(), actorAccountId);
        return process;
    }

    /**
     * Transitions a contract from {@code DRAFT} to {@code OFFERED}.
     *
     * @param contractId the id of the contract to transition
     * @param actorAccountId the caller's account id
     */
    @Transactional
    public void offerContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);

        if (contract.getState() != ContractState.DRAFT) {
            throw unprocessable("Contract must be in DRAFT state to offer, but is: " + contract.getState());
        }

        contract.setState(ContractState.OFFERED);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.DRAFT.name(), ContractState.OFFERED.name(), actorAccountId);
    }

    /**
     * Transitions a contract from {@code OFFERED} to {@code ACCEPTED}, then attempts
     * auto-approval. If auto-approval succeeds the contract moves to {@code APPROVED}
     * and payment handling is triggered.
     *
     * <p>For prepaid contracts, payment handling transitions the contract to
     * {@code AWAITING_PAYMENT} and creates a Stripe Checkout Session. The checkout URL
     * is returned so the B2C app can redirect the customer's browser to Stripe.
     *
     * <p>For postpaid contracts, payment handling throws
     * {@link UnsupportedPaymentModelException} (HTTP 422) — periodic invoicing is not
     * yet implemented. The contract remains in {@code APPROVED}.
     *
     * <p>The auto-approval step is a placeholder that always approves. Business rules
     * for determining whether a contract requires manual SME approval (and should
     * instead move to {@code AWAITING_APPROVAL}) will be added in the future.
     *
     * @param contractId the id of the contract to transition
     * @param actorAccountId the caller's account id
     * @return the checkout URL for prepaid contracts, or {@code null} for postpaid
     *     (postpaid throws before returning)
     */
    @Transactional
    public String acceptContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);

        if (contract.getState() != ContractState.OFFERED) {
            throw unprocessable("Contract must be in OFFERED state to accept, but is: " + contract.getState());
        }

        contract.setState(ContractState.ACCEPTED);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.OFFERED.name(), ContractState.ACCEPTED.name(), actorAccountId);

        // Attempt auto-approval immediately after acceptance. Returns the checkout URL
        // for prepaid contracts (propagated up to the REST resource).
        return approveContract(contractId, actorAccountId);
    }

    /**
     * Transitions a contract from {@code ACCEPTED} to {@code APPROVED}, then triggers
     * payment handling.
     *
     * <p>This method is the approval placeholder. It always approves the contract.
     * In the future, business rules will determine whether a contract can be
     * auto-approved or must move to {@code AWAITING_APPROVAL} for manual SME review.
     *
     * <p>After approval, payment handling transitions a prepaid contract to
     * {@code AWAITING_PAYMENT} and creates a Stripe Checkout Session (returning the
     * checkout URL). For postpaid contracts, {@link UnsupportedPaymentModelException}
     * is thrown and the contract remains in {@code APPROVED}.
     *
     * @param contractId the id of the contract to approve
     * @param actorAccountId the account id that triggered the approval
     * @return the checkout URL for prepaid contracts, or {@code null} for postpaid
     *     (postpaid throws before returning)
     */
    @Transactional
    public String approveContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);

        if (contract.getState() != ContractState.ACCEPTED) {
            throw unprocessable("Contract must be in ACCEPTED state to approve, but is: " + contract.getState());
        }

        contract.setState(ContractState.APPROVED);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.ACCEPTED.name(), ContractState.APPROVED.name(), actorAccountId);

        // Trigger payment handling. Returns the checkout URL for prepaid contracts.
        return triggerPaymentHandling(contractId, actorAccountId);
    }

    /**
     * Payment handling for an approved contract.
     *
     * <p>For {@code PREPAID} contracts: transitions the contract to
     * {@code AWAITING_PAYMENT}, delegates to {@link PaymentService#createPaymentForContract}
     * to create a Stripe Checkout Session, and returns the checkout URL.
     *
     * <p>For {@code POSTPAID} contracts: throws {@link UnsupportedPaymentModelException}
     * (HTTP 422). Periodic invoicing is not yet implemented. The contract remains in
     * {@code APPROVED} — no transition, no payment created.
     *
     * @param contractId the id of the approved contract
     * @param actorAccountId the account id that triggered the payment flow
     * @return the checkout URL for prepaid contracts (never returns for postpaid)
     */
    @Transactional
    public String triggerPaymentHandling(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, actorAccountId);

        if (contract.getState() != ContractState.APPROVED) {
            throw unprocessable(
                "Contract must be in APPROVED state to trigger payment handling, but is: "
                    + contract.getState());
        }

        if (contract.getPaymentModel() == NonMultitenancyContract.PaymentModel.POSTPAID) {
            throw new UnsupportedPaymentModelException(
                "Postpaid payment model is not yet supported. Contract remains in APPROVED: "
                    + contractId);
        }

        // PREPAID: transition to AWAITING_PAYMENT, then create the checkout session.
        contract.setState(ContractState.AWAITING_PAYMENT);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.APPROVED.name(),
            ContractState.AWAITING_PAYMENT.name(), actorAccountId);

        CreatePaymentResponse response =
            paymentService.get().createPaymentForContract(contractId, actorAccountId);
        return response.getCheckoutUrl();
    }

    /**
     * Transitions a contract from {@code AWAITING_PAYMENT} to {@code RUNNING}.
     *
     * <p>Called by {@link PaymentService#handlePaymentResult} after a successful payment
     * webhook. The {@code actorAccountId} is {@code "system"} for webhook-triggered
     * transitions (no user context on the webhook path).
     *
     * @param contractId the id of the contract to transition
     * @param actorAccountId the actor that triggered the transition (system or user)
     */
    @Transactional
    public void transitionToRunning(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        if (contract == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Contract not found: " + contractId)
                    .build());
        }
        if (contract.getState() != ContractState.AWAITING_PAYMENT) {
            throw unprocessable(
                "Contract must be in AWAITING_PAYMENT state to transition to RUNNING, but is: "
                    + contract.getState());
        }

        contract.setState(ContractState.RUNNING);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.AWAITING_PAYMENT.name(),
            ContractState.RUNNING.name(), actorAccountId);
    }

    // ==================== private helpers ====================

    /**
     * Loads a contract and verifies that the caller is linked to it via
     * {@code T_contract_account_role} with role {@code CUSTOMER}.
     *
     * @throws WebApplicationException 404 if the contract does not exist
     * @throws WebApplicationException 403 if the caller is not linked to the contract
     */
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

    private NonMultitenancyProcessInstance loadProcess(String contractId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyProcessInstance p " +
                "WHERE p.contractId = :contractId",
                NonMultitenancyProcessInstance.class)
            .setParameter("contractId", contractId)
            .getSingleResult();
    }

    private void recordStep(
            NonMultitenancyProcessInstance process,
            String fromState,
            String toState,
            String actorAccountId) {

        NonMultitenancyProcessInstanceStep step = new NonMultitenancyProcessInstanceStep();
        step.setId(UUID.randomUUID().toString());
        step.setOrganisationId(process.getOrganisationId());
        step.setProcessInstance(process);
        step.setActorUserId(actorAccountId);
        step.setFromState(fromState != null ? fromState : "");
        step.setToState(toState);
        step.setStepTimestamp(LocalDateTime.now());
        em.persist(step);
    }

    private static WebApplicationException unprocessable(String message) {
        return new WebApplicationException(
            Response.status(422).entity(message).build());
    }
}
