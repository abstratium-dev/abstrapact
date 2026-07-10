package dev.abstratium.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_part_definition")
@Audited
public class PartDefinition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_definition_id", nullable = false)
    @JsonIgnore
    private ProductDefinition productDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_part_definition_id")
    @JsonIgnore
    private PartDefinition parentPart;

    @OneToMany(mappedBy = "parentPart", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<PartDefinition> childParts = new ArrayList<>();

    @Column(name = "part_code", length = 50, nullable = false)
    private String partCode;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "min_cardinality", nullable = false)
    private Integer minCardinality = 1;

    @Column(name = "max_cardinality", nullable = false)
    private Integer maxCardinality = 1;

    @OneToMany(mappedBy = "partDefinition", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<PartAttributeDefinition> attributes = new ArrayList<>();

    @OneToMany(mappedBy = "partDefinition")
    @JsonIgnore
    private List<PartInstance> partInstances = new ArrayList<>();

    public PartDefinition() {
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

    public ProductDefinition getProductDefinition() {
        return productDefinition;
    }

    public void setProductDefinition(ProductDefinition productDefinition) {
        this.productDefinition = productDefinition;
    }

    public PartDefinition getParentPart() {
        return parentPart;
    }

    public void setParentPart(PartDefinition parentPart) {
        this.parentPart = parentPart;
    }

    public List<PartDefinition> getChildParts() {
        return childParts;
    }

    public void setChildParts(List<PartDefinition> childParts) {
        this.childParts = childParts;
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getMinCardinality() {
        return minCardinality;
    }

    public void setMinCardinality(Integer minCardinality) {
        this.minCardinality = minCardinality;
    }

    public Integer getMaxCardinality() {
        return maxCardinality;
    }

    public void setMaxCardinality(Integer maxCardinality) {
        this.maxCardinality = maxCardinality;
    }

    public List<PartAttributeDefinition> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<PartAttributeDefinition> attributes) {
        this.attributes = attributes;
    }

    public List<PartInstance> getPartInstances() {
        return partInstances;
    }

    public void setPartInstances(List<PartInstance> partInstances) {
        this.partInstances = partInstances;
    }
}
