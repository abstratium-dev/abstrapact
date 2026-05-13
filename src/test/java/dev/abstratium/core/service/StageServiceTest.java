package dev.abstratium.core.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(StageServiceTest.TestProfile.class)
class StageServiceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("abstratium.stage", "test");
        }
    }

    @Inject
    StageService stageService;

    @Test
    void testIsTestReturnsTrueForTestStage() {
        assertTrue(stageService.isTest(), "isTest() should return true when stage is 'test'");
    }

    @Test
    void testIsDevReturnsFalseForTestStage() {
        assertFalse(stageService.isDev(), "isDev() should return false when stage is 'test'");
    }

    @Test
    void testIsProdReturnsFalseForTestStage() {
        assertFalse(stageService.isProd(), "isProd() should return false when stage is 'test'");
    }

    @Test
    void testGetStageReturnsCorrectValue() {
        assertEquals("test", stageService.getStage(), "getStage() should return the configured stage value");
    }
}
