package dev.abstratium.product.service;

import dev.abstratium.core.service.OrgScopedCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductCodeCodecTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String RAW = "MOBILE-PLAN-PRO";
    private static final String ENCODED = ORG + "::" + RAW;

    @Test
    void encodeProducesOrgColonColonRawCode() {
        assertEquals(ENCODED, OrgScopedCodec.encode(ORG, RAW, "Product"));
    }

    @Test
    void decodeReturnsRawCode() {
        assertEquals(RAW, OrgScopedCodec.decode(ENCODED, "Product"));
    }

    @Test
    void extractOrgIdReturnsOrg() {
        assertEquals(ORG, OrgScopedCodec.extractOrgId(ENCODED, "Product"));
    }

    @Test
    void encodeDecodeRoundTrip() {
        String encoded = OrgScopedCodec.encode(ORG, RAW, "Product");
        assertEquals(RAW, OrgScopedCodec.decode(encoded, "Product"));
        assertEquals(ORG, OrgScopedCodec.extractOrgId(encoded, "Product"));
    }

    @Test
    void decodeHandlesRawCodeContainingDoubleColon() {
        String rawWithSep = "PLAN::PREMIUM";
        String encoded = OrgScopedCodec.encode(ORG, rawWithSep, "Product");
        assertEquals(rawWithSep, OrgScopedCodec.decode(encoded, "Product"));
        assertEquals(ORG, OrgScopedCodec.extractOrgId(encoded, "Product"));
    }

    @Test
    void encodeThrowsOnNullOrgId() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.encode(null, RAW, "Product"));
    }

    @Test
    void encodeThrowsOnBlankOrgId() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.encode("  ", RAW, "Product"));
    }

    @Test
    void encodeThrowsOnNullRawCode() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.encode(ORG, null, "Product"));
    }

    @Test
    void encodeThrowsOnBlankRawCode() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.encode(ORG, "", "Product"));
    }

    @Test
    void decodeThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.decode(null, "Product"));
    }

    @Test
    void decodeThrowsOnUnprefixedCode() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.decode("PLAIN-CODE", "Product"));
    }

    @Test
    void extractOrgIdThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.extractOrgId(null, "Product"));
    }

    @Test
    void extractOrgIdThrowsOnUnprefixedCode() {
        assertThrows(IllegalArgumentException.class, () -> OrgScopedCodec.extractOrgId("PLAIN-CODE", "Product"));
    }

    @Test
    void isPrefixedReturnsTrueForPrefixedValue() {
        assertTrue(OrgScopedCodec.isPrefixed(ENCODED));
    }

    @Test
    void isPrefixedReturnsFalseForRawCode() {
        assertFalse(OrgScopedCodec.isPrefixed(RAW));
    }

    @Test
    void isPrefixedReturnsFalseForNull() {
        assertFalse(OrgScopedCodec.isPrefixed(null));
    }

    @Test
    void contextAppearsInErrorMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> OrgScopedCodec.decode("PLAIN-CODE", "Part"));
        assertTrue(ex.getMessage().contains("Part"));
    }
}
