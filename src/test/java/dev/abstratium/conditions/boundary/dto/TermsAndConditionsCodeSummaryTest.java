package dev.abstratium.conditions.boundary.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TermsAndConditionsCodeSummaryTest {

    @Test
    void shouldCreateWithConstructor() {
        TermsAndConditionsCodeSummary summary = new TermsAndConditionsCodeSummary("TC-001", "Terms Title");
        assertEquals("TC-001", summary.getCode());
        assertEquals("Terms Title", summary.getTitle());
    }

    @Test
    void shouldCreateWithDefaultConstructor() {
        TermsAndConditionsCodeSummary summary = new TermsAndConditionsCodeSummary();
        assertNull(summary.getCode());
        assertNull(summary.getTitle());
    }

    @Test
    void shouldSetAndGetCode() {
        TermsAndConditionsCodeSummary summary = new TermsAndConditionsCodeSummary();
        summary.setCode("TC-002");
        assertEquals("TC-002", summary.getCode());
    }

    @Test
    void shouldSetAndGetTitle() {
        TermsAndConditionsCodeSummary summary = new TermsAndConditionsCodeSummary();
        summary.setTitle("New Title");
        assertEquals("New Title", summary.getTitle());
    }
}
