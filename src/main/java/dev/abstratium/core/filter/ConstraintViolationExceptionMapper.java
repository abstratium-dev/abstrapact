package dev.abstratium.core.filter;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;

import dev.abstratium.core.boundary.ErrorCode;
import io.quarkiverse.resteasy.problem.HttpProblem;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Hibernate ConstraintViolationException to RFC 7807 Problem Details.
 *
 * <p>MySQL duplicate entry errors (ErrorCode 1062, SQLState 23000) and H2 unique
 * constraint violations are mapped to HTTP 409 Conflict with a structured
 * {@code application/problem+json} response. All other constraint violations are
 * mapped to HTTP 500 Internal Server Error.
 *
 * <p>This prevents opaque 500 responses when a client attempts to create a resource
 * with a value that already exists (e.g., a duplicate stage name).
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        if (isDuplicateEntryViolation(exception)) {
            HttpProblem problem = HttpProblem.builder()
                .withStatus(Response.Status.CONFLICT)
                .withTitle(ErrorCode.DUPLICATE_ENTRY.getDescription())
                .withDetail(extractDuplicateDetail(exception))
                .withType(ErrorCode.DUPLICATE_ENTRY.getTypeUri())
                .build();
            return problem.toResponse();
        }

        HttpProblem problem = HttpProblem.builder()
            .withStatus(Response.Status.INTERNAL_SERVER_ERROR)
            .withTitle(ErrorCode.DATABASE_ERROR.getDescription())
            .withDetail(extractDuplicateDetail(exception))
            .withType(ErrorCode.DATABASE_ERROR.getTypeUri())
            .build();
        return problem.toResponse();
    }

    private boolean isDuplicateEntryViolation(ConstraintViolationException exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }

        // MySQL duplicate entry
        if (message.contains("Duplicate entry")) {
            return true;
        }

        // H2 unique constraint violation
        if (message.contains("Unique index or primary key violation")) {
            return true;
        }

        // Fallback: inspect the underlying SQLException for MySQL error code 1062
        Throwable cause = exception.getCause();
        if (cause instanceof SQLException sqlException) {
            if (sqlException.getErrorCode() == 1062) {
                return true;
            }
            String sqlMessage = sqlException.getMessage();
            if (sqlMessage != null && sqlMessage.contains("Duplicate entry")) {
                return true;
            }
        }

        // Check constraint name for known unique constraint patterns
        String constraintName = exception.getConstraintName();
        if (constraintName != null && constraintName.toUpperCase().contains("UQ_")) {
            return true;
        }

        return false;
    }

    private String extractDuplicateDetail(ConstraintViolationException exception) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return "A database constraint was violated.";
    }
}
