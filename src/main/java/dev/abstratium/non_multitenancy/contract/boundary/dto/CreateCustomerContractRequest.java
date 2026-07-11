package dev.abstratium.non_multitenancy.contract.boundary.dto;

import java.util.ArrayList;
import java.util.List;

public class CreateCustomerContractRequest {

    private String orgId;
    private String contractReference;
    private String publicNotes;
    private List<CustomerLineItemRequest> lineItems = new ArrayList<>();

    public CreateCustomerContractRequest() {
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public String getPublicNotes() {
        return publicNotes;
    }

    public void setPublicNotes(String publicNotes) {
        this.publicNotes = publicNotes;
    }

    public List<CustomerLineItemRequest> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<CustomerLineItemRequest> lineItems) {
        this.lineItems = lineItems;
    }
}
