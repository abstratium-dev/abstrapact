package dev.abstratium.core.filter;

import io.quarkiverse.resteasy.problem.HttpProblem;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps IllegalArgumentException to RFC 7807 Problem Details with HTTP 400 Bad Request.
 *
 * This ensures that business-rule violations thrown as IllegalArgumentException
 * from services are returned to the client as structured problem+json responses
 * rather than opaque 500 Internal Server Error responses.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        HttpProblem problem = HttpProblem.builder()
            .withStatus(Response.Status.BAD_REQUEST)
            .withTitle("Bad Request")
            .withDetail(exception.getMessage())
            .build();
        return problem.toResponse();
    }
}
