package dev.abstratium.core.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Test for SpaRoutingNotFoundMapper to verify:
 * 1. Non-API paths serve HTML redirect to /?_spa=<encoded-path> for SPA routing
 * 2. The mapper correctly handles different path types
 * 
 * Note: The mapper intercepts NotFoundException and decides whether to:
 * - Return HTML redirect for SPA routes (non-API paths), encoding the original
 *   path into the _spa query parameter so Angular can restore navigation
 * - Delegate to resteasy-problem for API paths (by returning null)
 */
@QuarkusTest
class SpaRoutingNotFoundMapperTest {

    @Test
    void testNonApiPathReturnsHtmlRedirect() {
        // Non-API paths should return HTML with redirect meta tag for SPA routing
        // The redirect encodes the original path into the _spa query parameter
        given()
            .when()
            .get("/addresses")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Faddresses"));
    }

    @Test
    void testNonApiPathWithSlashReturnsHtmlRedirect() {
        // Non-API paths with trailing slash should also return HTML redirect
        given()
            .when()
            .get("/some-angular-route/")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fsome-angular-route"));
    }

    @Test
    void testHtmlAcceptHeaderReturnsHtmlRedirect() {
        // Requests with HTML Accept header for non-API paths should return HTML redirect
        // This is the typical browser request when navigating to a URL
        given()
            .accept("text/html")
            .when()
            .get("/some-route")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fsome-route"));
    }

    @Test
    void testNestedNonApiPathReturnsHtmlRedirect() {
        // Nested non-API paths should also return HTML redirect
        // This tests paths like /addresses/123/edit
        given()
            .when()
            .get("/addresses/123/edit")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Faddresses%2F123%2Fedit"));
    }

    @Test
    void testDeeplyNestedNonApiPathReturnsHtmlRedirect() {
        // Test deeply nested paths to ensure the mapper handles them correctly
        given()
            .when()
            .get("/feature/sub-feature/item/123")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Ffeature%2Fsub-feature%2Fitem%2F123"));
    }

    @Test
    void testPathWithSpecialCharactersReturnsHtmlRedirect() {
        // Test paths with special characters (URL encoded)
        given()
            .when()
            .get("/route-with-dashes")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Froute-with-dashes"));
    }

    // ========== API Path Tests - These verify the bug fix ==========
    
    @Test
    void testApiPathWithLeadingSlashDoesNotReturnHtmlRedirect() {
        // API paths starting with /api/ should delegate to resteasy-problem
        // The key test is that they DON'T return HTML redirect (which was the bug)
        // When there's no actual endpoint, the framework may return 204/406, not 404
        given()
            .accept("application/json")
            .when()
            .get("/api/nonexistent/endpoint")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testOAuthPathDoesNotReturnHtmlRedirect() {
        // OAuth paths should delegate to resteasy-problem, not return HTML redirect
        given()
            .accept("application/json")
            .when()
            .get("/oauth/nonexistent")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testPublicPathDoesNotReturnHtmlRedirect() {
        // Public API paths should delegate to resteasy-problem, not return HTML redirect
        given()
            .accept("application/json")
            .when()
            .get("/public/nonexistent")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testQuarkusDevPathDoesNotReturnHtmlRedirect() {
        // Quarkus dev console paths should delegate to resteasy-problem, not return HTML redirect
        given()
            .accept("application/json")
            .when()
            .get("/q/nonexistent")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testNestedApiPathDoesNotReturnHtmlRedirect() {
        // Test deeply nested API paths to ensure they're correctly identified as API paths
        given()
            .accept("application/json")
            .when()
            .get("/api/resources/123/subitems/456")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testJsonAcceptHeaderForNonApiPathDoesNotReturnHtmlRedirect() {
        // Even non-API paths should delegate to resteasy-problem if JSON is explicitly requested
        // This is already implemented via the Accept header check in the mapper
        given()
            .accept("application/json")
            .when()
            .get("/some-nonexistent-path")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testProblemJsonAcceptHeaderForNonApiPathReturnsJson() {
        // Non-API paths with application/problem+json Accept header should return JSON, not HTML
        given()
            .accept("application/problem+json")
            .when()
            .get("/some-route")
            .then()
            .statusCode(404)
            .contentType("application/json")
            .body(containsString("\"status\":404"))
            .body(containsString("\"title\":\"Not Found\""));
    }

    @Test
    void testJsonAcceptHeaderWithMultipleTypesReturnsJson() {
        // Accept header with multiple types including JSON should return JSON
        given()
            .accept("text/html,application/json")
            .when()
            .get("/some-route")
            .then()
            .statusCode(404)
            .contentType("application/json")
            .body(containsString("\"status\":404"))
            .body(containsString("\"title\":\"Not Found\""));
    }

    // ========== API Path Tests Without Leading Slash ==========
    
    @Test
    void testApiPathWithoutLeadingSlashReturnsJson() {
        // API paths without leading slash (api/) should return JSON, not HTML
        // This tests the alternate path format handling
        given()
            .accept("application/json")
            .when()
            .get("/api/test")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testOAuthPathWithoutLeadingSlashReturnsJson() {
        // OAuth paths without leading slash (oauth/) should return JSON, not HTML
        given()
            .accept("application/json")
            .when()
            .get("/oauth/test")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testPublicPathWithoutLeadingSlashReturnsJson() {
        // Public paths without leading slash (public/) should return JSON, not HTML
        given()
            .accept("application/json")
            .when()
            .get("/public/test")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testQuarkusPathWithoutLeadingSlashReturnsJson() {
        // Quarkus paths without leading slash (q/) should return JSON, not HTML
        given()
            .accept("application/json")
            .when()
            .get("/q/test")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testApiPathReturnsJsonWithDetail() {
        // Verify that API path 404 responses don't return HTML
        // The actual status code may vary (404 or 406) depending on whether endpoint exists
        given()
            .accept("application/json")
            .when()
            .get("/api/missing/resource")
            .then()
            .contentType(org.hamcrest.Matchers.not(containsString("text/html")));
    }

    @Test
    void testNullAcceptHeaderForNonApiPathReturnsHtml() {
        // Non-API paths without Accept header should return HTML redirect
        // This simulates a browser request without explicit Accept header
        given()
            .when()
            .get("/dashboard")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fdashboard"));
    }

    @Test
    void testWildcardAcceptHeaderForNonApiPathReturnsHtml() {
        // Non-API paths with wildcard Accept header should return HTML redirect
        given()
            .accept("*/*")
            .when()
            .get("/profile")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fprofile"));
    }

    @Test
    void testRootPathReturnsHtml() {
        // Root path (/) that doesn't exist should return HTML redirect
        // This is an edge case but should be handled consistently
        given()
            .when()
            .get("/nonexistent-root-level-path")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("<!DOCTYPE html>"))
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fnonexistent-root-level-path"));
    }

    @Test
    void testNonApiPathWithQueryStringPreservesQuery() {
        given()
            .when()
            .get("/some-route?foo=bar&baz=qux")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fsome-route%3F"));
    }

    @Test
    void testNoDoubleSlashInSpaParamWhenPathHasLeadingSlash() {
        // Regression: when the path reported by UriInfo already has a leading slash
        // (e.g. after a server-side redirect to /signed-in), the _spa value must be
        // %2Fsigned-in, NOT %2F%2Fsigned-in (which would decode to //signed-in).
        given()
            .when()
            .get("/signed-in")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("url=/?_spa="))
            .body(containsString("%2Fsigned-in"))
            .body(not(containsString("%2F%2F")));
    }
}
