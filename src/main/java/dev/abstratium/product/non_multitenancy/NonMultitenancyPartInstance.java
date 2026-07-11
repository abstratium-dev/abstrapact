package dev.abstratium.product.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_part_instance")
@Audited
public class NonMultitenancyPartInstance {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyProductInstance productInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_definition_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyPartDefinition partDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_part_instance_id")
    @JsonIgnore
    private NonMultitenancyPartInstance parentPartInstance;

    @OneToMany(mappedBy = "parentPartInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartInstance> childPartInstances = new ArrayList<>();

    @Column(name = "resolved_unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal resolvedUnitPrice = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "partInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartInstanceAttribute> attributes = new ArrayList<>();

    public NonMultitenancyPartInstance() {
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

    public NonMultitenancyProductInstance getProductInstance() {
        return productInstance;
    }

    public void setProductInstance(NonMultitenancyProductInstance productInstance) {
        this.productInstance = productInstance;
    }

    public NonMultitenancyPartDefinition getPartDefinition() {
        return partDefinition;
    }

    public void setPartDefinition(NonMultitenancyPartDefinition partDefinition) {
        this.partDefinition = partDefinition;
    }

    public NonMultitenancyPartInstance getParentPartInstance() {
        return parentPartInstance;
    }

    public void setParentPartInstance(NonMultitenancyPartInstance parentPartInstance) {
        this.parentPartInstance = parentPartInstance;
    }

    public List<NonMultitenancyPartInstance> getChildPartInstances() {
        return childPartInstances;
    }

    public void setChildPartInstances(List<NonMultitenancyPartInstance> childPartInstances) {
        this.childPartInstances = childPartInstances;
    }

    public BigDecimal getResolvedUnitPrice() {
        return resolvedUnitPrice;
    }

    public void setResolvedUnitPrice(BigDecimal resolvedUnitPrice) {
        this.resolvedUnitPrice = resolvedUnitPrice;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<NonMultitenancyPartInstanceAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<NonMultitenancyPartInstanceAttribute> attributes) {
        this.attributes = attributes;
    }
}
