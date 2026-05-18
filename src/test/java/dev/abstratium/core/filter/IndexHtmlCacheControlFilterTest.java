package dev.abstratium.core.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Test for IndexHtmlCacheControlFilter to verify cache-prevention headers are applied
 * to index.html responses while not affecting other resources.
 */
@QuarkusTest
class IndexHtmlCacheControlFilterTest {

    @Test
    void testRootPathHasNoCacheHeaders() {
        // Root path (/) should have cache-prevention headers
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .header("Cache-Control", equalTo("no-cache, no-store, must-revalidate, proxy-revalidate"))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testIndexHtmlPathHasNoCacheHeaders() {
        // Explicit index.html path should have cache-prevention headers
        given()
            .when()
            .get("/index.html")
            .then()
            .statusCode(200)
            .header("Cache-Control", equalTo("no-cache, no-store, must-revalidate, proxy-revalidate"))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testPublicPathDoesNotHaveCacheControlHeadersFromFilter() {
        // Public paths should not have the index.html cache headers applied
        // Using /public/config which is a valid public endpoint
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .header("Cache-Control", org.hamcrest.Matchers.not(equalTo("no-cache, no-store, must-revalidate, proxy-revalidate")));
    }
}
