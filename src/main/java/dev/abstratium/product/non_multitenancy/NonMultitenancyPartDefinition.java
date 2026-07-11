package dev.abstratium.product.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_part_definition")
@Audited
public class NonMultitenancyPartDefinition {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_definition_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyProductDefinition productDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_part_definition_id")
    @JsonIgnore
    private NonMultitenancyPartDefinition parentPart;

    @OneToMany(mappedBy = "parentPart", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartDefinition> childParts = new ArrayList<>();

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "choice_group_id")
    private NonMultitenancyPartDefinitionChoiceGroup choiceGroup;

    @OneToMany(mappedBy = "parentPartDefinition", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartDefinitionChoiceGroup> choiceGroups = new ArrayList<>();

    @OneToMany(mappedBy = "partDefinition", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartAttributeDefinition> attributes = new ArrayList<>();

    public NonMultitenancyPartDefinition() {
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

    public NonMultitenancyProductDefinition getProductDefinition() {
        return productDefinition;
    }

    public void setProductDefinition(NonMultitenancyProductDefinition productDefinition) {
        this.productDefinition = productDefinition;
    }

    public NonMultitenancyPartDefinition getParentPart() {
        return parentPart;
    }

    public void setParentPart(NonMultitenancyPartDefinition parentPart) {
        this.parentPart = parentPart;
    }

    public List<NonMultitenancyPartDefinition> getChildParts() {
        return childParts;
    }

    public void setChildParts(List<NonMultitenancyPartDefinition> childParts) {
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

    public NonMultitenancyPartDefinitionChoiceGroup getChoiceGroup() {
        return choiceGroup;
    }

    public void setChoiceGroup(NonMultitenancyPartDefinitionChoiceGroup choiceGroup) {
        this.choiceGroup = choiceGroup;
    }

    public List<NonMultitenancyPartDefinitionChoiceGroup> getChoiceGroups() {
        return choiceGroups;
    }

    public void setChoiceGroups(List<NonMultitenancyPartDefinitionChoiceGroup> choiceGroups) {
        this.choiceGroups = choiceGroups;
    }

    public List<NonMultitenancyPartAttributeDefinition> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<NonMultitenancyPartAttributeDefinition> attributes) {
        this.attributes = attributes;
    }
}
