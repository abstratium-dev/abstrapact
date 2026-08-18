package dev.abstratium.test.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Helper to generate valid Stripe webhook signatures for tests.
 *
 * <p>Stripe's webhook signature scheme (as implemented by {@code com.stripe.net.Webhook}):
 * <pre>
 *   Stripe-Signature: t=<timestamp>,v1=<hex-hmac-sha256(payload, "<timestamp>.<payload>", secret)>
 * </pre>
 *
 * <p>This helper produces signatures that pass {@code Webhook.constructEvent} verification,
 * so tests can exercise the full signature-verification path without mocking the Stripe SDK.
 */
public final class WebhookSignatureTestHelper {

    private WebhookSignatureTestHelper() {
    }

    /**
     * Generates a valid {@code Stripe-Signature} header value for the given payload and
     * webhook secret.
     *
     * @param payload  the raw webhook payload body (UTF-8)
     * @param secret   the Stripe webhook signing secret (with or without the
     *                 {@code whsec_} prefix)
     * @param timestamp the unix timestamp (seconds) to embed in the signature
     * @return the {@code Stripe-Signature} header value, e.g.
     *     {@code "t=1234567890,v1=abcdef..."}
     */
    public static String sign(String payload, String secret, long timestamp) {
        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String hexHash = bytesToHex(hash);
            return "t=" + timestamp + ",v1=" + hexHash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign webhook payload", e);
        }
    }

    /**
     * Generates a valid signature with the current system time as the timestamp.
     */
    public static String sign(String payload, String secret) {
        return sign(payload, secret, System.currentTimeMillis() / 1000);
    }

    /**
     * Generates a <em>malformed</em> signature (random v1 value) that will fail
     * verification. Useful for testing the 400 response path.
     */
    public static String malformedSignature(String payload, long timestamp) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return "t=" + timestamp + ",v1=" + bytesToHex(random);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
