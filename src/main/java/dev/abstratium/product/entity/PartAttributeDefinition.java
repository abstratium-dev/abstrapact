package dev.abstratium.product.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_part_attribute_definition")
@Audited
public class PartAttributeDefinition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_definition_id", nullable = false)
    private PartDefinition partDefinition;

    @Column(name = "attribute_name", length = 50, nullable = false)
    private String attributeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 20, nullable = false)
    private DataType dataType;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    @OneToMany(mappedBy = "attributeDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartAttributeAllowedValue> allowedValues = new ArrayList<>();

    public PartAttributeDefinition() {
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

    public PartDefinition getPartDefinition() {
        return partDefinition;
    }

    public void setPartDefinition(PartDefinition partDefinition) {
        this.partDefinition = partDefinition;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<PartAttributeAllowedValue> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<PartAttributeAllowedValue> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public enum DataType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DATE
    }
}
