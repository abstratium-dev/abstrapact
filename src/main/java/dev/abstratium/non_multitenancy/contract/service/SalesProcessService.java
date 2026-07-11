package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.conditions.non_multitenancy.NonMultitenancyContract;
import dev.abstratium.process.entity.ProcessInstanceState;
import dev.abstratium.process.non_multitenancy.NonMultitenancyProcessInstance;
import dev.abstratium.process.non_multitenancy.NonMultitenancyProcessInstanceStep;
import jakarta.enterprise.context.ApplicationScoped;
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
 * Each contract has an associated {@link NonMultitenancyProcessInstance} whose
 * {@code processName} is {@code "sales-process:<contractId>"}.
 */
@ApplicationScoped
public class SalesProcessService {

    private static final String PROCESS_NAME_PREFIX = "sales-process:";
    private static final String PROCESS_VERSION = "1.0";

    @Inject
    EntityManager em;

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
        process.setProcessName(PROCESS_NAME_PREFIX + contract.getId());
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
        NonMultitenancyContract contract = loadContract(contractId);

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
     * Transitions a contract from {@code OFFERED} to {@code ACCEPTED}.
     *
     * @param contractId the id of the contract to transition
     * @param actorAccountId the caller's account id
     */
    @Transactional
    public void acceptContract(String contractId, String actorAccountId) {
        NonMultitenancyContract contract = loadContract(contractId);

        if (contract.getState() != ContractState.OFFERED) {
            throw unprocessable("Contract must be in OFFERED state to accept, but is: " + contract.getState());
        }

        contract.setState(ContractState.ACCEPTED);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        NonMultitenancyProcessInstance process = loadProcess(contractId);
        recordStep(process, ContractState.OFFERED.name(), ContractState.ACCEPTED.name(), actorAccountId);
    }

    // ==================== private helpers ====================

    private NonMultitenancyContract loadContract(String contractId) {
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        if (contract == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Contract not found: " + contractId)
                    .build());
        }
        return contract;
    }

    private NonMultitenancyProcessInstance loadProcess(String contractId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyProcessInstance p " +
                "WHERE p.processName = :name",
                NonMultitenancyProcessInstance.class)
            .setParameter("name", PROCESS_NAME_PREFIX + contractId)
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
