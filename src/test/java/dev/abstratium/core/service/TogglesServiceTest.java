package dev.abstratium.core.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(TogglesServiceTest.TestProfile.class)
class TogglesServiceTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "abstratium.toggles.api.url", "http://localhost:19999",
                    "abstratium.toggles.cache.ttl-seconds", "60",
                    "abstratium.stage", "test",
                    "abstratium.toggles.context", "abstratium-public",
                    "ABSTRATIUM_TOGGLES_CONTEXT", "abstratium-public"
            );
        }
    }

    @Inject
    TogglesService togglesService;

    static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(19999));
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        togglesService.clearCache();
    }

    @Test
    void testEmptyToggleNamesReturnsEmptyMap() {
        Map<String, String> result = togglesService.getToggleValues(Set.of(), Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testNullToggleNamesReturnsEmptyMap() {
        Map<String, String> result = togglesService.getToggleValues(null, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testCatchAllRuleReturnsValue() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-catchall",
                            "toggleDescription": "Test catchall toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-catchall"), Map.of());

        assertEquals("enabled", result.get("test-catchall"));
    }

    @Test
    void testMissingToggleReturnsOff() {
        String responseJson = """
                {
                    "toggles": [],
                    "queryMetadata": {
                        "count": 0,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-missing"), Map.of());

        assertEquals("off", result.get("test-missing"));
    }

    @Test
    void testApiErrorReturnsOff() {
        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse().withStatus(500)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-error"), Map.of());

        assertEquals("off", result.get("test-error"));
    }

    @Test
    void testRuleCriteriaRegexMatching() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-regex",
                            "toggleDescription": "Test regex toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "specific",
                            "priority": 1,
                            "value": "special",
                            "ruleCriteria": [
                                { "criterionKey": "userId", "criterionValue": "admin" }
                            ]
                        },
                        {
                            "toggleName": "test-regex",
                            "toggleDescription": "Test regex toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 2,
                            "value": "off",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 2,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> matchResult = togglesService.getToggleValues(
                Set.of("test-regex"), Map.of("userId", "admin"));
        assertEquals("special", matchResult.get("test-regex"));

        Map<String, String> noMatchResult = togglesService.getToggleValues(
                Set.of("test-regex"), Map.of("userId", "user1"));
        assertEquals("off", noMatchResult.get("test-regex"));
    }

    @Test
    void testRuleCriteriaMissingKeyUsesEmptyString() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-missing-key",
                            "toggleDescription": "Test missing key toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "specific",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": [
                                { "criterionKey": "userId", "criterionValue": "" }
                            ]
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-missing-key"), Map.of());

        assertEquals("enabled", result.get("test-missing-key"));
    }

    @Test
    void testSlashDelimitedRegexWithFlags() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-regex-flags",
                            "toggleDescription": "Test regex flags toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "region",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": [
                                { "criterionKey": "country", "criterionValue": "/^DE$/i" }
                            ]
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-regex-flags"), Map.of("country", "de"));

        assertEquals("enabled", result.get("test-regex-flags"));
    }

    @Test
    void testInvalidRegexFallsBackToExactMatch() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-invalid-regex",
                            "toggleDescription": "Test invalid regex toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "bad",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": [
                                { "criterionKey": "mode", "criterionValue": "[invalid" }
                            ]
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> exactMatchResult = togglesService.getToggleValues(
                Set.of("test-invalid-regex"), Map.of("mode", "[invalid"));
        assertEquals("enabled", exactMatchResult.get("test-invalid-regex"));

        Map<String, String> noMatchResult = togglesService.getToggleValues(
                Set.of("test-invalid-regex"), Map.of("mode", "other"));
        assertEquals("off", noMatchResult.get("test-invalid-regex"));
    }

    @Test
    void testNoRuleMatchesReturnsOff() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-no-match",
                            "toggleDescription": "Test no match toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "specific",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": [
                                { "criterionKey": "userId", "criterionValue": "admin" }
                            ]
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-no-match"), Map.of("userId", "user1"));

        assertEquals("off", result.get("test-no-match"));
    }

    @Test
    void testMultipleTogglesInOneRequest() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "toggle-a",
                            "toggleDescription": "Toggle A",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "on",
                            "ruleCriteria": []
                        },
                        {
                            "toggleName": "toggle-b",
                            "toggleDescription": "Toggle B",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "off",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 2,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("toggle-a", "toggle-b"), Map.of());

        assertEquals("on", result.get("toggle-a"));
        assertEquals("off", result.get("toggle-b"));
    }

    @Test
    void testResponseIsCached() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-cached",
                            "toggleDescription": "Test cached toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "cached-value",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> firstResult = togglesService.getToggleValues(
                Set.of("test-cached"), Map.of());
        assertEquals("cached-value", firstResult.get("test-cached"));

        Map<String, String> secondResult = togglesService.getToggleValues(
                Set.of("test-cached"), Map.of());
        assertEquals("cached-value", secondResult.get("test-cached"));

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/public/toggles")));
    }

    @Test
    void testNullClientContextTreatedAsEmpty() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-null-context",
                            "toggleDescription": "Test null context toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-null-context"), null);

        assertEquals("enabled", result.get("test-null-context"));
    }

    @Test
    void testPrioritySorting() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-priority",
                            "toggleDescription": "Test priority toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "low",
                            "priority": 3,
                            "value": "low-priority",
                            "ruleCriteria": []
                        },
                        {
                            "toggleName": "test-priority",
                            "toggleDescription": "Test priority toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "high",
                            "priority": 1,
                            "value": "high-priority",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 2,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-priority"), Map.of());

        assertEquals("high-priority", result.get("test-priority"));
    }

    @Test
    void testNullCriterionValueDoesNotMatch() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-null-criterion",
                            "toggleDescription": "Test null criterion toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "specific",
                            "priority": 1,
                            "value": "enabled",
                            "ruleCriteria": [
                                { "criterionKey": "userId", "criterionValue": null }
                            ]
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-null-criterion"), Map.of("userId", "admin"));

        assertEquals("off", result.get("test-null-criterion"));
    }

    @Test
    void testMalformedJsonReturnsOff() {
        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ invalid json ")));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-malformed"), Map.of());

        assertEquals("off", result.get("test-malformed"));
    }

    @Test
    void testMixedToggleNamesFiltersCorrectly() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "wanted-toggle",
                            "toggleDescription": "Wanted toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "on",
                            "ruleCriteria": []
                        },
                        {
                            "toggleName": "other-toggle",
                            "toggleDescription": "Other toggle",
                            "toggleEnabled": true,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "off",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 2,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("wanted-toggle"), Map.of());

        assertEquals("on", result.get("wanted-toggle"));
    }

    @Test
    void testDisabledToggleReturnsOffImmediately() {
        String responseJson = """
                {
                    "toggles": [
                        {
                            "toggleName": "test-disabled",
                            "toggleDescription": "Test disabled toggle",
                            "toggleEnabled": false,
                            "stageName": "test",
                            "ruleName": "default",
                            "priority": 1,
                            "value": "should-not-return-this",
                            "ruleCriteria": []
                        }
                    ],
                    "queryMetadata": {
                        "count": 1,
                        "cacheHit": false
                    }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/public/toggles"))
                .withQueryParam("stage", equalTo("test"))
                .withQueryParam("context", equalTo("abstratium-public"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        Map<String, String> result = togglesService.getToggleValues(
                Set.of("test-disabled"), Map.of());

        assertEquals("off", result.get("test-disabled"));
    }
}
