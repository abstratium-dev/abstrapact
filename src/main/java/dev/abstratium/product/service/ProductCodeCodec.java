package dev.abstratium.product.service;

public final class ProductCodeCodec {

    private static final String SEPARATOR = "::";

    private ProductCodeCodec() {
    }

    public static String encode(String orgId, String rawProductCode) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException("orgId must not be blank");
        }
        if (rawProductCode == null || rawProductCode.isBlank()) {
            throw new IllegalArgumentException("rawProductCode must not be blank");
        }
        return orgId + SEPARATOR + rawProductCode;
    }

    public static String decode(String storedValue) {
        int idx = requireSeparator(storedValue);
        return storedValue.substring(idx + SEPARATOR.length());
    }

    public static String extractOrgId(String storedValue) {
        int idx = requireSeparator(storedValue);
        return storedValue.substring(0, idx);
    }

    private static int requireSeparator(String storedValue) {
        if (storedValue == null) {
            throw new IllegalArgumentException("storedValue must not be null");
        }
        int idx = storedValue.indexOf(SEPARATOR);
        if (idx < 0) {
            throw new IllegalArgumentException("Not a prefixed product code (missing '::'): " + storedValue);
        }
        return idx;
    }
}
