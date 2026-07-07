package dev.abstratium.product.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_product_instance")
@Audited
public class ProductInstance {

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

    @OneToMany(mappedBy = "productInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PartInstance> partInstances = new ArrayList<>();

    public ProductInstance() {
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

    public List<PartInstance> getPartInstances() {
        return partInstances;
    }

    public void setPartInstances(List<PartInstance> partInstances) {
        this.partInstances = partInstances;
    }
}
