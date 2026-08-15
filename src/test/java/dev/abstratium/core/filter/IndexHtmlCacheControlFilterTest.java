package dev.abstratium.core.filter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Test for IndexHtmlCacheControlFilter to verify cache-prevention headers are applied
 * to index.html responses while not affecting other resources.
 *
 * In tests, Quinoa is disabled, so / and /index.html return 404 (caught by
 * SpaRoutingNotFoundMapper). The Vert.x filter still fires and adds headers.
 * In production, Quinoa serves index.html with 200 and the headers are present there too.
 */
@QuarkusTest
class IndexHtmlCacheControlFilterTest {

    @Test
    void testRootPathHasNoCacheHeaders() {
        given()
            .when()
            .get("/")
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Cache-Control", equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testIndexHtmlPathHasNoCacheHeaders() {
        given()
            .when()
            .get("/index.html")
            .then()
            .statusCode(anyOf(is(200), is(404)))
            .header("Cache-Control", equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE))
            .header("Pragma", equalTo("no-cache"))
            .header("Expires", equalTo("0"));
    }

    @Test
    void testPublicPathDoesNotHaveCacheControlHeadersFromFilter() {
        given()
            .when()
            .get("/public/config")
            .then()
            .statusCode(200)
            .header("Cache-Control", org.hamcrest.Matchers.not(equalTo(IndexHtmlCacheControlFilter.CACHE_CONTROL_VALUE)));
    }
}
