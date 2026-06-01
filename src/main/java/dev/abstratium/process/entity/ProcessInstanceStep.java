package dev.abstratium.process.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Table(name = "T_process_instance_step")
@Audited
public class ProcessInstanceStep {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_instance_id", nullable = false)
    @JsonIgnore
    private ProcessInstance processInstance;

    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "step_timestamp", nullable = false)
    private LocalDateTime stepTimestamp;

    @Column(name = "from_state", length = 20, nullable = false)
    private String fromState;

    @Column(name = "to_state", length = 20, nullable = false)
    private String toState;

    @Column(name = "reason", length = 500)
    private String reason;

    public ProcessInstanceStep() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public void setProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
