package dev.abstratium.core.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

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
 */
@Provider
public class IndexHtmlCacheControlFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Only apply to index.html and root path
        if (isIndexHtmlRequest(path)) {
            // Prevent all caching of index.html
            responseContext.getHeaders().putSingle("Cache-Control", "no-cache, no-store, must-revalidate, proxy-revalidate");
            responseContext.getHeaders().putSingle("Pragma", "no-cache");
            responseContext.getHeaders().putSingle("Expires", "0");
        }
    }

    private boolean isIndexHtmlRequest(String path) {
        return path == null
            || path.isEmpty()
            || path.equals("/")
            || path.equals("index.html")
            || path.endsWith("/index.html");
    }
}
