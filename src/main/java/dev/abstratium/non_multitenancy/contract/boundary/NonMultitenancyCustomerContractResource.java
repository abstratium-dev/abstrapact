package dev.abstratium.non_multitenancy.contract.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.non_multitenancy.contract.boundary.dto.*;
import dev.abstratium.non_multitenancy.contract.service.NonMultitenancyCustomerContractService;
import dev.abstratium.non_multitenancy.contract.service.NonMultitenancyOrganisationResolutionService;
import dev.abstratium.non_multitenancy.contract.service.SalesProcessService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;

/**
 * Cross-tenant REST resource for customer contracts.
 *
 * Resource methods are intentionally <strong>not</strong> {@code @Transactional}.
 * Each method resolves the seller {@code orgId} first, sets it into
 * {@link CurrentOrgContext}, and only then calls the {@code @Transactional} service.
 */
@Path("/api/public/contracts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.USER)
public class NonMultitenancyCustomerContractResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    NonMultitenancyOrganisationResolutionService orgResolutionService;

    @Inject
    NonMultitenancyCustomerContractService contractService;

    @Inject
    SalesProcessService salesProcessService;

    @POST
    @Operation(summary = "Create a new contract draft from product codes")
    public Response create(CreateCustomerContractRequest request) {
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("At least one line item is required").build();
        }
        if (request.getOrgId() == null || request.getOrgId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("orgId is required").build();
        }

        prefixAllCodes(request);

        List<String> productCodes = request.getLineItems().stream()
            .map(CustomerLineItemRequest::getProductCode)
            .toList();

        String sellerOrgId = orgResolutionService.resolveSellerOrgId(productCodes);
        currentOrgContext.setOrgId(sellerOrgId);

        String accountId = accountId();
        CustomerContractResponse response = contractService.createContract(request, sellerOrgId, accountId);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Operation(summary = "List contracts linked to the caller's account")
    public List<CustomerContractSummary> list(@QueryParam("orgId") String orgId) {
        return contractService.listContracts(accountId(), orgId);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a single contract by id")
    public CustomerContractResponse get(@PathParam("id") String id) {
        return contractService.getContract(id, accountId());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a draft contract")
    public CustomerContractResponse update(
            @PathParam("id") String id,
            CreateCustomerContractRequest request) {

        if (request.getOrgId() == null || request.getOrgId().isBlank()) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("orgId is required").build());
        }

        prefixAllCodes(request);

        List<String> productCodes = request.getLineItems().stream()
            .map(CustomerLineItemRequest::getProductCode)
            .toList();

        String sellerOrgId = orgResolutionService.resolveSellerOrgId(productCodes);
        currentOrgContext.setOrgId(sellerOrgId);

        return contractService.updateContract(id, request, sellerOrgId, accountId());
    }

    @DELETE
    @Path("/{id}/line-items/{lineItemId}")
    @Operation(summary = "Remove a line item from a draft contract")
    public CustomerContractResponse deleteLineItem(
            @PathParam("id") String id,
            @PathParam("lineItemId") String lineItemId) {

        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);

        return contractService.deleteLineItem(id, lineItemId, sellerOrgId, accountId());
    }

    @POST
    @Path("/{id}/offer")
    @Operation(summary = "Move the contract from DRAFT to OFFERED")
    public Response offer(@PathParam("id") String id) {
        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);

        salesProcessService.offerContract(id, accountId());
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/accept")
    @Operation(summary = "Move the contract from OFFERED to ACCEPTED")
    public Response accept(@PathParam("id") String id) {
        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);

        salesProcessService.acceptContract(id, accountId());
        return Response.ok().build();
    }

    // ==================== private helpers ====================

    private String accountId() {
        return identity.getPrincipal().getName();
    }

    /**
     * Reads the {@code organisationId} directly from the non-tenant contract row,
     * without opening a tenant-scoped Hibernate session.
     */
    private String resolveOrgIdFromContract(String contractId) {
        return contractService.getOrgIdForContract(contractId);
    }

    /**
     * Prefixes all raw product codes and part codes in the request with the top-level orgId.
     * Callers send raw short codes; this method converts them to {@code {orgId}::{rawCode}}
     * in-place before validation and persistence.
     */
    private void prefixAllCodes(CreateCustomerContractRequest request) {
        String orgId = request.getOrgId();
        if (request.getLineItems() == null) return;
        for (CustomerLineItemRequest li : request.getLineItems()) {
            li.setProductCode(OrgScopedCodec.encode(orgId, li.getProductCode(), "Product"));
            prefixPartInstanceCodes(orgId, li.getPartInstances());
        }
    }

    private void prefixPartInstanceCodes(String orgId, List<PartInstanceRequest> partInstances) {
        if (partInstances == null) return;
        for (PartInstanceRequest pi : partInstances) {
            pi.setPartCode(OrgScopedCodec.encode(orgId, pi.getPartCode(), "Part"));
            prefixPartInstanceCodes(orgId, pi.getChildPartInstances());
        }
    }
}
