package dev.abstratium.conditions.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.conditions.boundary.dto.TermsAndConditionsCodeSummary;
import dev.abstratium.conditions.entity.TermsAndConditions;
import dev.abstratium.conditions.service.TermsAndConditionsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;

@Path("/api/terms-and-conditions")
@Tag(name = "Terms and Conditions", description = "Operations for managing terms and conditions documents")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TermsAndConditionsResource {

    @Inject
    TermsAndConditionsService service;

    @GET
    @Operation(summary = "List all terms and conditions")
    public List<TermsAndConditions> listAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get terms and conditions by ID")
    public Response getById(@PathParam("id") String id) {
        Optional<TermsAndConditions> terms = service.findById(id);
        if (terms.isPresent()) {
            return Response.ok(terms.get()).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/code/{code}")
    @Operation(summary = "Get terms and conditions by code")
    public Response getByCode(@PathParam("code") String code) {
        List<TermsAndConditions> terms = service.findByCode(code);
        if (!terms.isEmpty()) {
            return Response.ok(terms).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Operation(summary = "Create new terms and conditions")
    public Response create(TermsAndConditions terms) {
        if (terms.getCode() == null || terms.getCode().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Code is required").build();
        }
        TermsAndConditions created = service.create(terms);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update existing terms and conditions")
    public Response update(@PathParam("id") String id, TermsAndConditions terms) {
        Optional<TermsAndConditions> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        terms.setId(id);
        TermsAndConditions updated = service.update(terms);
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete terms and conditions")
    public Response delete(@PathParam("id") String id) {
        Optional<TermsAndConditions> existing = service.findById(id);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        service.delete(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/codes")
    @Operation(summary = "List distinct terms and conditions codes with titles")
    public List<TermsAndConditionsCodeSummary> listDistinctCodes() {
        return service.findDistinctCodes();
    }
}
