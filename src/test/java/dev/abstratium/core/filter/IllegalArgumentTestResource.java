package dev.abstratium.core.filter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only JAX-RS resource that throws IllegalArgumentException,
 * used to verify that IllegalArgumentExceptionMapper maps it correctly.
 */
@Path("/api/test/illegal-argument")
public class IllegalArgumentTestResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void triggerIllegalArgument(@QueryParam("value") String value) {
        throw new IllegalArgumentException("Illegal argument: " + value);
    }
}
