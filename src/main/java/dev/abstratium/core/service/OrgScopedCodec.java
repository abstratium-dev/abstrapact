package dev.abstratium.core.service;

/**
 * Utility for encoding and decoding organisation-scoped codes.
 *
 * Stored format: {@code {orgId}::{rawCode}}
 *
 * Used for product codes, part codes, terms-and-conditions codes, and any
 * other value that must be unique within an organisation but may collide
 * across organisations when stored in a non-multitenancy table.
 */
public final class OrgScopedCodec {

    private static final String SEPARATOR = "::";

    private OrgScopedCodec() {
    }

    /**
     * @param orgId       the organisation UUID
     * @param rawCode     the raw short code (e.g. {@code MOBILE-PLAN-PRO})
     * @param context     human-readable context for error messages (e.g. {@code "Product"})
     */
    public static String encode(String orgId, String rawCode, String context) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException(context + " orgId must not be blank");
        }
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException(context + " raw code must not be blank");
        }
        return orgId + SEPARATOR + rawCode;
    }

    /**
     * Extracts the raw code from a prefixed stored value.
     *
     * @param storedValue the stored value in {@code {orgId}::{rawCode}} format
     * @param context     human-readable context for error messages
     */
    public static String decode(String storedValue, String context) {
        int idx = requireSeparator(storedValue, context);
        return storedValue.substring(idx + SEPARATOR.length());
    }

    /**
     * Extracts the {@code orgId} from a prefixed stored value.
     *
     * @param storedValue the stored value in {@code {orgId}::{rawCode}} format
     * @param context     human-readable context for error messages
     */
    public static String extractOrgId(String storedValue, String context) {
        int idx = requireSeparator(storedValue, context);
        return storedValue.substring(0, idx);
    }

    /**
     * Returns {@code true} if {@code value} already contains the separator and is therefore
     * already prefixed. Useful to avoid double-encoding.
     */
    public static boolean isPrefixed(String value) {
        return value != null && value.contains(SEPARATOR);
    }

    private static int requireSeparator(String storedValue, String context) {
        if (storedValue == null) {
            throw new IllegalArgumentException(context + " stored value must not be null");
        }
        int idx = storedValue.indexOf(SEPARATOR);
        if (idx < 0) {
            throw new IllegalArgumentException(
                "Not a prefixed " + context + " code (missing '::'): " + storedValue);
        }
        return idx;
    }
}
