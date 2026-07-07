package dev.abstratium.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "T_part_instance_attribute")
@Audited
public class PartInstanceAttribute {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_instance_id", nullable = false)
    @JsonIgnore
    private PartInstance partInstance;

    @Column(name = "attribute_name", length = 50, nullable = false)
    private String attributeName;

    @Column(name = "attribute_value", length = 255, nullable = false)
    private String attributeValue;

    public PartInstanceAttribute() {
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

    public PartInstance getPartInstance() {
        return partInstance;
    }

    public void setPartInstance(PartInstance partInstance) {
        this.partInstance = partInstance;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }
}
