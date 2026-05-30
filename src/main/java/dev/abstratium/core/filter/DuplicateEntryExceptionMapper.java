package dev.abstratium.core.filter;

import java.sql.SQLException;

import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.hibernate.exception.ConstraintViolationException;

import dev.abstratium.core.boundary.ErrorCode;
import io.quarkiverse.resteasy.problem.HttpProblem;

/**
 * Catches exceptions whose root cause is a Hibernate duplicate-entry constraint
 * violation and maps them to HTTP 409 Conflict.
 *
 * <p>Hibernate constraint violations that occur during transaction commit are
 * typically wrapped by the JTA transaction manager (e.g. in a
 * {@code HeuristicMixedException} or {@code RollbackException}) before they
 * reach the JAX-RS layer. This mapper walks the exception chain, locates the
 * underlying {@code ConstraintViolationException}, and returns a structured
 * {@code application/problem+json} response.
 *
 * <p>For all other exceptions it falls back to the same generic 500 behaviour as
 * {@code quarkus-resteasy-problem}'s default mapper so that no other error
 * handling is lost.
 */
@Provider
@Priority(1)
public class DuplicateEntryExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        ConstraintViolationException constraintViolation = findConstraintViolationCause(exception);

        if (constraintViolation != null && isDuplicateEntryViolation(constraintViolation)) {
            HttpProblem problem = HttpProblem.builder()
                .withStatus(Response.Status.CONFLICT)
                .withTitle(ErrorCode.DUPLICATE_ENTRY.getDescription())
                .withDetail(extractDuplicateDetail(constraintViolation))
                .withType(ErrorCode.DUPLICATE_ENTRY.getTypeUri())
                .build();
            return problem.toResponse();
        }

        // Fallback: mimic quarkus-resteasy-problem's DefaultExceptionMapper
        HttpProblem problem = HttpProblem.builder()
            .withStatus(Response.Status.INTERNAL_SERVER_ERROR)
            .withTitle("Internal Server Error")
            .withDetail(exception.getMessage())
            .build();
        return problem.toResponse();
    }

    private ConstraintViolationException findConstraintViolationCause(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof ConstraintViolationException) {
                return (ConstraintViolationException) throwable;
            }
            throwable = throwable.getCause();
        }
        return null;
    }

    private boolean isDuplicateEntryViolation(ConstraintViolationException exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }

        if (message.contains("Duplicate entry")) {
            return true;
        }

        if (message.contains("Unique index or primary key violation")) {
            return true;
        }

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

        String constraintName = exception.getConstraintName();
        if (constraintName != null && constraintName.toUpperCase().contains("UQ_")) {
            return true;
        }

        return false;
    }

    private String extractDuplicateDetail(ConstraintViolationException exception) {
        String raw = exception.getMessage();
        if (raw == null || raw.isBlank()) {
            Throwable cause = exception.getCause();
            if (cause != null && cause.getMessage() != null) {
                raw = cause.getMessage();
            }
        }

        if (raw != null) {
            int start = raw.indexOf("Duplicate entry '");
            if (start != -1) {
                start += "Duplicate entry ".length();
                int valueEnd = raw.indexOf('\'', start + 1);
                if (valueEnd != -1) {
                    String duplicateValue = raw.substring(start + 1, valueEnd);

                    int keyStart = raw.indexOf("for key '", valueEnd);
                    if (keyStart != -1) {
                        keyStart += "for key ".length();
                        int keyEnd = raw.indexOf('\'', keyStart + 1);
                        if (keyEnd != -1) {
                            String fullKey = raw.substring(keyStart + 1, keyEnd);
                            String tableName = fullKey;
                            int dot = fullKey.indexOf('.');
                            if (dot != -1) {
                                tableName = fullKey.substring(0, dot);
                            }
                            String entity = toFriendlyEntityName(tableName);
                            return "A " + entity + " with name '" + duplicateValue + "' already exists. Please choose a different name.";
                        }
                    }

                    return "The value '" + duplicateValue + "' already exists. Please choose a different value.";
                }
            }

            if (raw.contains("Unique index or primary key violation")) {
                String constraint = exception.getConstraintName();
                String entity = constraint != null ? toFriendlyEntityName(constraint) : "resource";
                return "A " + entity + " with the provided value already exists. Please choose a different value.";
            }
        }

        return "A resource with the provided value already exists. Please choose a different value.";
    }

    private String toFriendlyEntityName(String raw) {
        if (raw == null) {
            return "resource";
        }
        String name = raw;
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(dot + 1);
        }
        if (name.toUpperCase().startsWith("UQ_")) {
            name = name.substring(3);
        }
        if (name.toUpperCase().startsWith("T_")) {
            name = name.substring(2);
        }
        name = name.replace('_', ' ').toLowerCase();
        return name;
    }
}
