package dev.abstratium.process.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_process_instance")
@Audited
public class ProcessInstance {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @Column(name = "process_name", length = 100, nullable = false)
    private String processName;

    @Column(name = "process_version", length = 20)
    private String processVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private ProcessInstanceState state;

    @OneToMany(mappedBy = "processInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<ProcessInstanceStep> steps = new ArrayList<>();

    public ProcessInstance() {
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

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public ProcessInstanceState getState() {
        return state;
    }

    public void setState(ProcessInstanceState state) {
        this.state = state;
    }

    public List<ProcessInstanceStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ProcessInstanceStep> steps) {
        this.steps = steps;
    }
}
