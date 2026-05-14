package dev.abstratium.demo.boundary.api;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.core.boundary.ErrorCode;
import dev.abstratium.core.boundary.FunctionalException;
import dev.abstratium.demo.Roles;
import dev.abstratium.demo.entity.Demo;
import dev.abstratium.demo.service.DemoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/demo")
@Tag(name = "Demo", description = "Demo endpoints")
public class DemoResource {

    @Inject
    DemoService demoService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public List<Demo> getAll() {
        return demoService.findAll();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public Demo create(Demo demo) {
        return demoService.create(demo);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public Demo update(Demo demo) {
        return demoService.update(demo);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public void delete(@PathParam("id") String id) {
        demoService.delete(id);
    }

    /**
     * Demo endpoint that throws a FunctionalException to demonstrate RFC 7807 Problem Details.
     * This endpoint always fails with a 400 Bad Request containing a structured error response.
     * Uses ErrorCode.DEMO_ERROR which provides a unique error code and wiki documentation URL.
     */
    @GET
    @Path("/error")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public void triggerError() {
        throw new FunctionalException(
            Response.Status.BAD_REQUEST,
            ErrorCode.DEMO_ERROR,
            "This is a demonstration of RFC 7807 Problem Details error handling. The error response follows the standard format with type, title, status, and detail fields."
        );
    }

    /**
     * Demo endpoint that throws an IllegalArgumentException to demonstrate the IllegalArgumentExceptionMapper.
     * Accepts an optional query parameter; if the value is "bad", it throws IllegalArgumentException.
     */
    @GET
    @Path("/illegal-argument")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.USER})
    public void triggerIllegalArgument(@jakarta.ws.rs.QueryParam("value") String value) {
        throw new IllegalArgumentException("Illegal argument: " + value);
    }

}
