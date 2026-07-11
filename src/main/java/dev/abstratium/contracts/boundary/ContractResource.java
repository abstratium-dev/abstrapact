package dev.abstratium.contracts.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.conditions.entity.Contract;
import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.contracts.boundary.dto.ContractSummary;
import dev.abstratium.contracts.boundary.dto.CreateDraftContractRequest;
import dev.abstratium.contracts.service.ContractService;
import dev.abstratium.core.service.CurrentOrgContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;

@Path("/api/contracts")
@Tag(name = "Contracts", description = "Operations for managing contracts")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContractResource {

    @Inject
    ContractService service;

    @Inject
    CurrentOrgContext currentOrgContext;

    @POST
    @Operation(summary = "Create a new draft contract with line items")
    public Response createDraft(CreateDraftContractRequest request) {
        try {
            Contract created = service.createDraft(request, currentOrgContext.getOrgId());
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(422).entity(e.getMessage()).build();
        }
    }

    @GET
    @Operation(summary = "List all contracts")
    public List<ContractSummary> listAll() {
        return service.findAll(currentOrgContext.getOrgId());
    }

    @GET
    @Path("/state/{state}")
    @Operation(summary = "List contracts by state")
    public List<ContractSummary> listByState(@PathParam("state") ContractState state) {
        return service.findByState(state);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a contract by ID")
    public Response getById(@PathParam("id") String id) {
        Optional<Contract> contract = service.findById(id);
        if (contract.isPresent()) {
            return Response.ok(contract.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a draft contract")
    public Response delete(@PathParam("id") String id) {
        Optional<Contract> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            service.delete(id);
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }
}
