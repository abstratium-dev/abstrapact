package dev.abstratium.product.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductCodeCodecTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String RAW = "MOBILE-PLAN-PRO";
    private static final String ENCODED = ORG + "::" + RAW;

    @Test
    void encodeProducesOrgColonColonRawCode() {
        assertEquals(ENCODED, ProductCodeCodec.encode(ORG, RAW));
    }

    @Test
    void decodeReturnsRawCode() {
        assertEquals(RAW, ProductCodeCodec.decode(ENCODED));
    }

    @Test
    void extractOrgIdReturnsOrg() {
        assertEquals(ORG, ProductCodeCodec.extractOrgId(ENCODED));
    }

    @Test
    void encodeDecodeRoundTrip() {
        String encoded = ProductCodeCodec.encode(ORG, RAW);
        assertEquals(RAW, ProductCodeCodec.decode(encoded));
        assertEquals(ORG, ProductCodeCodec.extractOrgId(encoded));
    }

    @Test
    void decodeHandlesRawCodeContainingDoubleColon() {
        String rawWithSep = "PLAN::PREMIUM";
        String encoded = ProductCodeCodec.encode(ORG, rawWithSep);
        assertEquals(rawWithSep, ProductCodeCodec.decode(encoded));
        assertEquals(ORG, ProductCodeCodec.extractOrgId(encoded));
    }

    @Test
    void encodeThrowsOnNullOrgId() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.encode(null, RAW));
    }

    @Test
    void encodeThrowsOnBlankOrgId() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.encode("  ", RAW));
    }

    @Test
    void encodeThrowsOnNullRawCode() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.encode(ORG, null));
    }

    @Test
    void encodeThrowsOnBlankRawCode() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.encode(ORG, ""));
    }

    @Test
    void decodeThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.decode(null));
    }

    @Test
    void decodeThrowsOnUnprefixedCode() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.decode("PLAIN-CODE"));
    }

    @Test
    void extractOrgIdThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.extractOrgId(null));
    }

    @Test
    void extractOrgIdThrowsOnUnprefixedCode() {
        assertThrows(IllegalArgumentException.class, () -> ProductCodeCodec.extractOrgId("PLAIN-CODE"));
    }
}
