package dev.abstratium.product.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_product_instance")
@Audited
public class NonMultitenancyProductInstance {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_definition_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyProductDefinition productDefinition;

    @OneToMany(mappedBy = "productInstance", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyPartInstance> partInstances = new ArrayList<>();

    public NonMultitenancyProductInstance() {
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

    public List<NonMultitenancyPartInstance> getPartInstances() {
        return partInstances;
    }

    public void setPartInstances(List<NonMultitenancyPartInstance> partInstances) {
        this.partInstances = partInstances;
    }
}
