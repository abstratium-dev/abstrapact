package dev.abstratium.conditions.non_multitenancy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.abstratium.product.non_multitenancy.NonMultitenancyProductInstance;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "T_contract_line_item")
@Audited
public class NonMultitenancyContractLineItem {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "organisation_id", length = 36, nullable = false)
    private String organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_instance_id", nullable = false)
    @JsonIgnore
    private NonMultitenancyProductInstance productInstance;

    @Column(name = "line_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "contractLineItem", cascade = CascadeType.REMOVE)
    @JsonIgnore
    private List<NonMultitenancyContractTermsLink> specialTermsLinks = new ArrayList<>();

    public NonMultitenancyContractLineItem() {
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

    public NonMultitenancyContract getContract() {
        return contract;
    }

    public void setContract(NonMultitenancyContract contract) {
        this.contract = contract;
    }

    public NonMultitenancyProductInstance getProductInstance() {
        return productInstance;
    }

    public void setProductInstance(NonMultitenancyProductInstance productInstance) {
        this.productInstance = productInstance;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public List<NonMultitenancyContractTermsLink> getSpecialTermsLinks() {
        return specialTermsLinks;
    }

    public void setSpecialTermsLinks(List<NonMultitenancyContractTermsLink> specialTermsLinks) {
        this.specialTermsLinks = specialTermsLinks;
    }
}
