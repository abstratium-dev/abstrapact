package dev.abstratium.core;

/**
 * Build information for the Abstracore baseline.
 * 
 * The BUILD_TIMESTAMP is automatically updated by the pre-commit hook
 * to track which version of the baseline is deployed in each application.
 * 
 * This allows operators to identify which applications need updates when
 * new baseline versions are released.
 */
public final class BuildInfo {
    
    /**
     * ISO-8601 timestamp of the last commit to this baseline.
     * Format: yyyy-MM-dd'T'HH:mm:ss'Z' (UTC)
     * 
     * This value is automatically updated by .git/hooks/pre-commit
     * DO NOT EDIT MANUALLY - changes will be overwritten
     */
    public static final String BUILD_TIMESTAMP = "2026-05-13T19:29:13Z";
    
    private BuildInfo() {
        // Utility class - prevent instantiation
    }
}
