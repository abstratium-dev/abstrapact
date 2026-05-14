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

        if (isForeignKeyViolation(exception)) {
            HttpProblem problem = HttpProblem.builder()
                .withStatus(Response.Status.CONFLICT)
                .withTitle(ErrorCode.FOREIGN_KEY_VIOLATION.getDescription())
                .withDetail(extractForeignKeyDetail(exception))
                .withType(ErrorCode.FOREIGN_KEY_VIOLATION.getTypeUri())
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

    private boolean isForeignKeyViolation(ConstraintViolationException exception) {
        String message = exception.getMessage();
        if (message == null) {
            message = "";
        }

        // MySQL FK violation messages
        if (message.contains("Cannot delete or update a parent row")) {
            return true;
        }
        if (message.contains("Cannot add or update a child row")) {
            return true;
        }

        // Fallback: inspect underlying SQLException for MySQL FK error codes
        Throwable cause = exception.getCause();
        if (cause instanceof SQLException sqlException) {
            int errorCode = sqlException.getErrorCode();
            // MySQL: 1451 = delete parent with children, 1452 = insert child without parent
            if (errorCode == 1451 || errorCode == 1452) {
                return true;
            }
            String sqlMessage = sqlException.getMessage();
            if (sqlMessage != null && (
                sqlMessage.contains("Cannot delete or update a parent row") ||
                sqlMessage.contains("Cannot add or update a child row"))) {
                return true;
            }
        }

        // Check constraint name for known FK patterns
        String constraintName = exception.getConstraintName();
        if (constraintName != null && constraintName.toUpperCase().contains("FK_")) {
            return true;
        }

        return false;
    }

    private String extractForeignKeyDetail(ConstraintViolationException exception) {
        String raw = exception.getMessage();
        if (raw == null || raw.isBlank()) {
            Throwable cause = exception.getCause();
            if (cause != null && cause.getMessage() != null) {
                raw = cause.getMessage();
            }
        }

        if (raw != null && raw.contains("Cannot delete or update a parent row")) {
            String constraint = exception.getConstraintName();
            String referenced = extractReferencedEntity(raw, constraint);
            return "This " + referenced + " is still referenced by other data and cannot be deleted. Remove the dependent data first.";
        }

        if (raw != null && raw.contains("Cannot add or update a child row")) {
            return "The referenced resource does not exist. Please ensure all related resources are created first.";
        }

        return "This resource is still referenced by other data and cannot be deleted or modified.";
    }

    private String extractReferencedEntity(String raw, String constraintName) {
        if (constraintName != null) {
            String name = constraintName.toUpperCase();
            if (name.contains("STAGE")) {
                return "stage";
            }
            if (name.contains("TOGGLE")) {
                return "toggle";
            }
            if (name.contains("RULE")) {
                return "rule";
            }
        }
        if (raw != null) {
            if (raw.contains("T_stage")) {
                return "stage";
            }
            if (raw.contains("T_toggle")) {
                return "toggle";
            }
            if (raw.contains("T_toggle_rule")) {
                return "rule";
            }
        }
        return "resource";
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
