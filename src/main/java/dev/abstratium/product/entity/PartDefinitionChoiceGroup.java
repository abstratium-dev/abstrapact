package dev.abstratium.product.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_part_definition_choice_group")
@Audited
public class PartDefinitionChoiceGroup {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_part_definition_id", nullable = false)
    private PartDefinition parentPartDefinition;

    @Column(name = "min_choices", nullable = false)
    private Integer minChoices = 1;

    @Column(name = "max_choices", nullable = false)
    private Integer maxChoices = 1;

    public PartDefinitionChoiceGroup() {
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

    public PartDefinition getParentPartDefinition() {
        return parentPartDefinition;
    }

    public void setParentPartDefinition(PartDefinition parentPartDefinition) {
        this.parentPartDefinition = parentPartDefinition;
    }

    public Integer getMinChoices() {
        return minChoices;
    }

    public void setMinChoices(Integer minChoices) {
        this.minChoices = minChoices;
    }

    public Integer getMaxChoices() {
        return maxChoices;
    }

    public void setMaxChoices(Integer maxChoices) {
        this.maxChoices = maxChoices;
    }
}
