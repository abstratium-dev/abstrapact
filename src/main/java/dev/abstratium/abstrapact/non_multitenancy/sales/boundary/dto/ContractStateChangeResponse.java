package dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto;

import java.time.LocalDateTime;

/**
 * A single state change recorded for a sales process instance.
 *
 * <p>Represents the transition of a contract from one state to another,
 * together with the actor that triggered it and an optional reason.
 */
public class ContractStateChangeResponse {

    private String id;
    private String processInstanceId;
    private LocalDateTime stepTimestamp;
    private String fromState;
    private String toState;
    private String actorUserId;
    private String reason;

    public ContractStateChangeResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public LocalDateTime getStepTimestamp() {
        return stepTimestamp;
    }

    public void setStepTimestamp(LocalDateTime stepTimestamp) {
        this.stepTimestamp = stepTimestamp;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
