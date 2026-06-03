package dev.abstratium.core.service;

import dev.abstratium.core.entity.Config;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@QuarkusTest
class ConfigServiceTest {

    @Inject
    ConfigService configService;

    @Test
    void testGetOrCreateReturnsDefaults() {
        Config config = configService.getOrCreate();
        assertNotNull(config);
        assertNotNull(config.getId());
        assertNotNull(config.getOrganisationId());
        assertNotNull(config.getCurrencyCode());
        assertFalse(config.getCurrencyCode().isBlank());
        assertNotNull(config.getLocale());
        assertFalse(config.getLocale().isBlank());
        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
    }

    @Test
    void testGetOrCreateReturnsExisting() {
        // First call creates
        Config first = configService.getOrCreate();
        assertNotNull(first);

        // Second call returns existing (same row, not a new one)
        Config second = configService.getOrCreate();
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getCurrencyCode(), second.getCurrencyCode());
        assertEquals(first.getLocale(), second.getLocale());
    }

    @Test
    void testFindReturnsExisting() {
        configService.getOrCreate();

        Optional<Config> found = configService.find();
        assertTrue(found.isPresent());
    }

    @Test
    void testUpdateConfig() {
        configService.getOrCreate();

        Config updated = configService.update("USD", "de-DE");
        assertEquals("USD", updated.getCurrencyCode());
        assertEquals("de-DE", updated.getLocale());
        assertNotNull(updated.getUpdatedAt());
    }
}
