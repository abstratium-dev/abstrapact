package dev.abstratium.core.boundary;

import java.net.URI;

/**
 * Enumeration of all error codes used in the application.
 * Each error code maps to a unique URI that can be looked up in the wiki for detailed documentation.
 */
public enum ErrorCode {
    
    // Demo errors (1000-1999)
    DEMO_ERROR("ERR-1000", "Demo error for testing"),
    DEMO_NOT_FOUND("ERR-1001", "Demo entity not found"),
    DEMO_INVALID_INPUT("ERR-1002", "Invalid input for demo entity"),
    
    // Authentication/Authorization errors (2000-2999)
    UNAUTHORIZED("ERR-2000", "Unauthorized access"),
    FORBIDDEN("ERR-2001", "Forbidden resource"),
    INVALID_TOKEN("ERR-2002", "Invalid authentication token"),
    
    // Validation errors (3000-3999)
    VALIDATION_FAILED("ERR-3000", "Validation failed"),
    REQUIRED_FIELD_MISSING("ERR-3001", "Required field is missing"),
    INVALID_FORMAT("ERR-3002", "Invalid data format"),
    
    // Business logic errors (4000-4999)
    BUSINESS_RULE_VIOLATION("ERR-4000", "Business rule violation"),
    DUPLICATE_ENTRY("ERR-4001", "Duplicate entry detected"),
    INVALID_STATE("ERR-4002", "Invalid state for operation"),
    FOREIGN_KEY_VIOLATION("ERR-4003", "Resource is still referenced by other data"),
    
    // System errors (5000-5999)
    INTERNAL_ERROR("ERR-5000", "Internal system error"),
    DATABASE_ERROR("ERR-5001", "Database operation failed"),
    EXTERNAL_SERVICE_ERROR("ERR-5002", "External service unavailable");
    
    private final String code;
    private final String description;
    
    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    /**
     * Returns the error code (e.g., "ERR-1000")
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Returns a human-readable description of the error
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns the URI that points to the wiki documentation for this error.
     * This URI is used as the 'type' field in RFC 7807 Problem Details.
     */
    public URI getTypeUri() {
        return URI.create(code);
    }
    
    @Override
    public String toString() {
        return code + ": " + description;
    }
}
