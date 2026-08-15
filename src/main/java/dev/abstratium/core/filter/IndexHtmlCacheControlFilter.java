package dev.abstratium.core.filter;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Cache control filter for index.html to prevent "chunk not found" errors after deployments.
 *
 * Problem: Angular builds with outputHashing=all generate hashed filenames (chunk-ABC123.js).
 * When a new deployment occurs, these hashes change. If index.html is cached, browsers
 * try to load non-existent chunks, causing "Failed to fetch dynamically imported module" errors.
 *
 * Solution: This filter adds cache-prevention headers specifically for index.html,
 * ensuring browsers always fetch the fresh version with correct chunk references.
 * Hashed assets (with content hash in filename) can still be cached long-term.
 *
 * This filter runs at the Vert.x layer (not JAX-RS) so it fires for Quinoa-served
 * static resources, which bypass the JAX-RS pipeline entirely.
 */
@ApplicationScoped
public class IndexHtmlCacheControlFilter {

    static final String CACHE_CONTROL_VALUE =
        "no-cache, no-store, must-revalidate, proxy-revalidate";

    void registerRoute(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(rc -> {
            String path = rc.request().path();

            if (isIndexHtmlRequest(path)) {
                rc.response().headers()
                    .set("Cache-Control", CACHE_CONTROL_VALUE)
                    .set("Pragma", "no-cache")
                    .set("Expires", "0");
            }

            rc.next();
        });
    }

    private boolean isIndexHtmlRequest(String path) {
        return path == null
            || path.isEmpty()
            || path.equals("/")
            || path.equals("/index.html")
            || path.endsWith("/index.html");
    }
}
