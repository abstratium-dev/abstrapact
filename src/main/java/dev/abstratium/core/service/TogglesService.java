package dev.abstratium.core.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.oidc.IdToken;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ApplicationScoped
public class TogglesService {

    private static final Logger log = LoggerFactory.getLogger(TogglesService.class);

    @ConfigProperty(name = "abstratium.toggles.api.url")
    String togglesApiUrl;

    @ConfigProperty(name = "abstratium.toggles.context", defaultValue = "abstratium-public")
    String toggleContext;

    @ConfigProperty(name = "abstratium.toggles.cache.ttl-seconds", defaultValue = "30")
    long cacheTtlSeconds;

    @ConfigProperty(name = "abstratium.toggles.cache.max-size-bytes", defaultValue = "5000000")
    long maxCacheSizeBytes;

    @Inject
    StageService stageService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @IdToken
    JsonWebToken idToken;

    private Client client;
    private Cache<String, String> cache;

    public TogglesService() {
    }

    @PostConstruct
    void init() {
        this.client = ClientBuilder.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumWeight(maxCacheSizeBytes)
                .weigher((String key, String value) -> value.getBytes(StandardCharsets.UTF_8).length)
                .build();
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Clears the toggle cache. Used for testing.
     */
    void clearCache() {
        cache.invalidateAll();
    }

    /**
     * Gets toggle values using default context derived from the current JWT token.
     * The userId is set to the email from the JWT if present, otherwise "anonymous".
     *
     * @param toggleNames set of toggle names to evaluate
     * @return map of toggle names to their values
     */
    public Map<String, String> getToggleValues(Set<String> toggleNames) {
        Map<String, String> defaultContext = new HashMap<>();
        String userId = idToken != null && idToken.getClaim("email") != null
                ? idToken.getClaim("email")
                : "anonymous";
        defaultContext.put("userId", userId);
        return getToggleValues(toggleNames, defaultContext);
    }

    public Map<String, String> getToggleValues(Set<String> toggleNames, Map<String, String> clientContext) {
        if (toggleNames == null || toggleNames.isEmpty()) {
            log.debug("getToggleValues: empty toggle names, returning empty map");
            return Map.of();
        }

        log.debug("getToggleValues: evaluating {} toggles with context {}", toggleNames, clientContext);
        ToggleResponse response = fetchToggles();
        Map<String, String> result = new HashMap<>();
        for (String name : toggleNames) {
            String value = evaluateToggle(name, response, clientContext);
            log.debug("getToggleValues: toggle '{}' = '{}'", name, value);
            result.put(name, value);
        }
        log.debug("getToggleValues: completed evaluation for {} toggles", toggleNames.size());
        return result;
    }

    private ToggleResponse fetchToggles() {
        String stage = stageService.getStage();
        log.debug("fetchToggles: fetching toggles for stage '{}'", stage);

        String cachedJson = cache.getIfPresent(stage);
        if (cachedJson != null) {
            log.debug("fetchToggles: cache HIT for stage '{}'", stage);
            try {
                ToggleResponse cached = objectMapper.readValue(cachedJson, ToggleResponse.class);
                log.debug("fetchToggles: returning {} toggles from cache", cached.toggles().size());
                return cached;
            } catch (Exception e) {
                log.warn("fetchToggles: failed to deserialize cached toggle response, invalidating cache", e);
                cache.invalidate(stage);
            }
        }

        log.debug("fetchToggles: cache MISS for stage '{}', calling API at {}", stage, togglesApiUrl);
        try {
            Response response = client.target(togglesApiUrl)
                    .path("public/toggles")
                    .queryParam("stage", stage)
                    .queryParam("context", toggleContext)
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            if (response.getStatus() != 200) {
                log.warn("fetchToggles: API returned status {} for stage={}, returning empty response", response.getStatus(), stage);
                return emptyResponse();
            }

            // Suppress unsafe null inference: Response is not annotated for null analysis
            @SuppressWarnings("null")
            String body = response.readEntity(String.class);
            ToggleResponse toggleResponse = objectMapper.readValue(body, ToggleResponse.class);
            if (toggleResponse == null) {
                log.warn("fetchToggles: deserialized response is null, returning empty response");
                return emptyResponse();
            }
            log.debug("fetchToggles: received {} toggles from API, caching response", toggleResponse.toggles().size());
            cache.put(stage, body);
            return toggleResponse;
        } catch (Exception e) {
            log.error("fetchToggles: failed to fetch toggles from API", e);
            return emptyResponse();
        }
    }

    private ToggleResponse emptyResponse() {
        return new ToggleResponse(List.of(), new QueryMetadata(0, false));
    }

    private String evaluateToggle(String toggleName, ToggleResponse response, Map<String, String> clientContext) {
        log.debug("evaluateToggle: evaluating '{}' with {} context entries", toggleName, clientContext != null ? clientContext.size() : 0);

        if (response == null || response.toggles() == null) {
            log.debug("evaluateToggle: null response or toggles, returning 'off'");
            return "off";
        }

        List<ToggleRow> matchingRows = response.toggles().stream()
                .filter(r -> toggleName.equals(r.toggleName()))
                .sorted(Comparator.comparingInt(ToggleRow::priority))
                .toList();

        log.debug("evaluateToggle: found {} matching rows for '{}'", matchingRows.size(), toggleName);

        if (matchingRows.isEmpty()) {
            log.debug("evaluateToggle: no rows found for '{}', returning 'off'", toggleName);
            return "off";
        }

        // Check if toggle is disabled - if so, return "off" immediately (per abstoggle algorithm)
        ToggleRow firstRow = matchingRows.get(0);
        if (!firstRow.toggleEnabled()) {
            log.debug("evaluateToggle: toggle '{}' is disabled (from stage '{}'), returning 'off'",
                    toggleName, firstRow.stageName());
            return "off";
        }

        log.debug("evaluateToggle: toggle '{}' enabled (from stage '{}'), checking {} rules",
                toggleName, firstRow.stageName(), matchingRows.size());

        Map<String, String> context = clientContext != null ? clientContext : Map.of();
        for (ToggleRow row : matchingRows) {
            log.debug("evaluateToggle: checking rule '{}' priority={} value='{}' criteria={}",
                    row.ruleName(), row.priority(), row.value(),
                    row.ruleCriteria() != null ? row.ruleCriteria().size() : 0);
            if (matchesCriteria(row.ruleCriteria(), context)) {
                log.debug("evaluateToggle: rule '{}' MATCHED, returning '{}'", row.ruleName(), row.value());
                return row.value();
            }
            log.debug("evaluateToggle: rule '{}' did not match", row.ruleName());
        }

        log.debug("evaluateToggle: no rules matched for '{}', returning 'off'", toggleName);
        return "off";
    }

    private boolean matchesCriteria(List<RuleCriterion> criteria, Map<String, String> clientContext) {
        if (criteria == null || criteria.isEmpty()) {
            log.debug("matchesCriteria: no criteria (catch-all), returning true");
            return true;
        }
        log.debug("matchesCriteria: checking {} criteria", criteria.size());
        for (RuleCriterion criterion : criteria) {
            String clientValue = clientContext.getOrDefault(criterion.criterionKey(), "");
            boolean matched = matchesPattern(criterion.criterionValue(), clientValue);
            log.debug("matchesCriteria: {}: clientValue='{}' pattern='{}' -> {}",
                    criterion.criterionKey(), clientValue, criterion.criterionValue(), matched);
            if (!matched) {
                log.debug("matchesCriteria: criterion '{}' did not match, aborting", criterion.criterionKey());
                return false;
            }
        }
        log.debug("matchesCriteria: all {} criteria matched", criteria.size());
        return true;
    }

    private boolean matchesPattern(String pattern, String value) {
        if (pattern == null) {
            return false;
        }
        if (pattern.startsWith("/") && pattern.lastIndexOf('/') > 0) {
            int lastSlash = pattern.lastIndexOf('/');
            String regex = pattern.substring(1, lastSlash);
            String flags = pattern.substring(lastSlash + 1);
            int javaFlags = 0;
            for (char c : flags.toCharArray()) {
                switch (c) {
                    case 'i' -> javaFlags |= Pattern.CASE_INSENSITIVE;
                    case 'm' -> javaFlags |= Pattern.MULTILINE;
                    case 's' -> javaFlags |= Pattern.DOTALL;
                    case 'x' -> javaFlags |= Pattern.COMMENTS;
                    case 'u' -> javaFlags |= Pattern.UNICODE_CASE;
                }
            }
            try {
                Pattern p = Pattern.compile(regex, javaFlags);
                return p.matcher(value).matches();
            } catch (PatternSyntaxException e) {
                return pattern.equals(value);
            }
        }
        try {
            Pattern p = Pattern.compile(pattern);
            return p.matcher(value).matches();
        } catch (PatternSyntaxException e) {
            return pattern.equals(value);
        }
    }
}
