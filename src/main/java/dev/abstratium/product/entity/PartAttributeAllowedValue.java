package dev.abstratium.product.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_part_attribute_allowed_value")
@Audited
public class PartAttributeAllowedValue {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_attribute_definition_id", nullable = false)
    private PartAttributeDefinition attributeDefinition;

    @Column(name = "allowed_value", length = 255, nullable = false)
    private String allowedValue;

    public PartAttributeAllowedValue() {
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

    public PartAttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public void setAttributeDefinition(PartAttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
    }

    public String getAllowedValue() {
        return allowedValue;
    }

    public void setAllowedValue(String allowedValue) {
        this.allowedValue = allowedValue;
    }
}
