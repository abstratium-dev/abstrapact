package dev.abstratium.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_part_instance")
@Audited
public class PartInstance {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @TenantId
    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id", nullable = false)
    @JsonIgnore
    private ProductInstance productInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_definition_id", nullable = false)
    @JsonIgnore
    private PartDefinition partDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_part_instance_id")
    @JsonIgnore
    private PartInstance parentPartInstance;

    @OneToMany(mappedBy = "parentPartInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<PartInstance> childPartInstances = new ArrayList<>();

    @Column(name = "resolved_unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal resolvedUnitPrice = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "partInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<PartInstanceAttribute> attributes = new ArrayList<>();

    public PartInstance() {
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

    public ProductInstance getProductInstance() {
        return productInstance;
    }

    public void setProductInstance(ProductInstance productInstance) {
        this.productInstance = productInstance;
    }

    public PartDefinition getPartDefinition() {
        return partDefinition;
    }

    public void setPartDefinition(PartDefinition partDefinition) {
        this.partDefinition = partDefinition;
    }

    public PartInstance getParentPartInstance() {
        return parentPartInstance;
    }

    public void setParentPartInstance(PartInstance parentPartInstance) {
        this.parentPartInstance = parentPartInstance;
    }

    public List<PartInstance> getChildPartInstances() {
        return childPartInstances;
    }

    public void setChildPartInstances(List<PartInstance> childPartInstances) {
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

    public List<PartInstanceAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<PartInstanceAttribute> attributes) {
        this.attributes = attributes;
    }
}
