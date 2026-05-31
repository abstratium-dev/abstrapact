package dev.abstratium.core.boundary;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/**
 * Test-only JAX-RS resource for exercising FunctionalException constructors
 * via @QuarkusTest HTTP requests so that JaCoCo counts the coverage.
 */
@Path("/api/test/functional-exception")
public class FunctionalExceptionTestResource {

    @GET
    @Path("/with-error-code")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwWithErrorCode() {
        throw new FunctionalException(
                Response.Status.CONFLICT,
                ErrorCode.DUPLICATE_ENTRY,
                "thrown with error code");
    }

    @GET
    @Path("/with-title")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwWithTitle() {
        throw new FunctionalException(
                Response.Status.BAD_REQUEST,
                "Custom Title",
                "thrown with custom title");
    }

    @GET
    @Path("/with-type-uri")
    @Produces(MediaType.APPLICATION_JSON)
    public void throwWithTypeUri() {
        throw new FunctionalException(
                Response.Status.NOT_FOUND,
                "Custom Title",
                "thrown with type uri",
                URI.create("https://example.com/errors/test"));
    }
}
