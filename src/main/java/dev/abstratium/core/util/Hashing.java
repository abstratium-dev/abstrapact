package dev.abstratium.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SHA-256 hashing utility for request fingerprinting in idempotency handling.
 */
public final class Hashing {

    private Hashing() {
    }

    /**
     * Returns the lower-case hex-encoded SHA-256 hash of the given string.
     *
     * @param input the string to hash; may be empty but not null
     * @return 64-character lower-case hex string
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
