package dev.abstratium.core.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Custom exception mapper for NotFoundException that enables SPA routing for Angular.
 * 
 * This mapper runs with HIGHER priority (1) than resteasy-problem's default mapper,
 * allowing it to intercept 404 errors for non-API paths and serve index.html instead.
 * 
 * The problem this solves:
 * - quarkus-resteasy-problem extension converts ALL NotFoundException to RFC-7807 Problem JSON
 * - This prevents Quinoa's SPA routing from working for Angular routes like /addresses
 * - When accessing http://localhost:8088/addresses directly, browser receives
 *   {"status":404,"title":"Not Found",...} instead of the Angular application
 * 
 * How it works:
 * - Runs with priority 1 (very high) to execute BEFORE resteasy-problem's mapper
 * - For API paths (/api/*, /oauth/*, /public/*, /q/*): returns proper 404 JSON response
 * - For non-API paths: serves HTML redirect to let Angular's router handle the route
 * 
 * This allows:
 * - API endpoints to return proper 404 JSON responses
 * - Angular routes to work when accessed directly in the browser
 */
@Provider
@Priority(1)
public class SpaRoutingNotFoundMapper implements ExceptionMapper<NotFoundException> {

    private static final Logger LOG = Logger.getLogger(SpaRoutingNotFoundMapper.class);

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(NotFoundException exception) {
        String path = uriInfo.getPath();
        
        // For API paths, return a proper 404 JSON response
        if (isApiPath(path)) {
            LOG.debugf("API path 404, returning 404 status: %s", path);
            return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"status\":404,\"title\":\"Not Found\",\"detail\":\"" + exception.getMessage() + "\"}")
                .build();
        }

        // Check if the request explicitly accepts JSON (likely an API call)
        String acceptHeader = headers.getHeaderString(HttpHeaders.ACCEPT);
        if (acceptHeader != null && 
            (acceptHeader.contains(MediaType.APPLICATION_JSON) || 
             acceptHeader.contains("application/problem+json"))) {
            LOG.debugf("JSON request 404, returning 404 status: %s", path);
            return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"status\":404,\"title\":\"Not Found\",\"detail\":\"" + exception.getMessage() + "\"}")
                .build();
        }

        // For non-API paths, redirect to root to let Angular handle routing
        LOG.debugf("Non-API path 404, redirecting to root for SPA routing: %s", path);
        
        // Return 200 OK with a redirect meta tag to let the browser load the Angular app
        // The Angular app will then handle the routing based on the URL
        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/\"></head><body></body></html>";
        return Response.ok(html, MediaType.TEXT_HTML).build();
    }

    /**
     * Check if the path is an API endpoint that should return RFC-7807 Problem JSON.
     * 
     * Checks for both formats (with and without leading slash) because different
     * JAX-RS implementations may return paths in different formats from UriInfo.getPath().
     */
    private boolean isApiPath(String path) {
        return path.startsWith("/api/") 
            || path.startsWith("api/")
            || path.startsWith("/oauth/") 
            || path.startsWith("oauth/")
            || path.startsWith("/public/")
            || path.startsWith("public/")
            || path.startsWith("/q/")
            || path.startsWith("q/");
    }
}
