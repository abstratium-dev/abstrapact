package dev.abstratium.core.filter;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Vert.x route handler that adds security headers to all responses.
 *
 * This filter implements defense-in-depth security by adding multiple
 * security-related HTTP headers to protect against common web vulnerabilities.
 *
 * This filter runs at the Vert.x layer (not JAX-RS) so it fires for all responses,
 * including Quinoa-served static resources which bypass the JAX-RS pipeline entirely.
 */
@ApplicationScoped
public class SecurityHeadersFilter {

    @ConfigProperty(name = "security.csp.enabled", defaultValue = "true")
    boolean cspEnabled;

    @ConfigProperty(name = "security.csp.policy", defaultValue =
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self' data:; " +
        "connect-src 'self'; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self'"
    )
    String cspPolicy;

    @ConfigProperty(name = "security.hsts.enabled", defaultValue = "false")
    boolean hstsEnabled;

    @ConfigProperty(name = "security.hsts.max-age", defaultValue = "31536000")
    int hstsMaxAge;

    @ConfigProperty(name = "security.hsts.include-subdomains", defaultValue = "true")
    boolean hstsIncludeSubDomains;

    @ConfigProperty(name = "security.hsts.preload", defaultValue = "true")
    boolean hstsPreload;

    void registerRoute(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(this::applySecurityHeaders);
    }

    void applySecurityHeaders(RoutingContext rc) {
        var headers = rc.response().headers();

        if (cspEnabled) {
            headers.set("Content-Security-Policy", cspPolicy);
        }
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-XSS-Protection", "1; mode=block");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Permissions-Policy",
            "geolocation=(), microphone=(), camera=(), payment=()");
        if (hstsEnabled) {
            StringBuilder hsts = new StringBuilder("max-age=" + hstsMaxAge);
            if (hstsIncludeSubDomains) {
                hsts.append("; includeSubDomains");
            }
            if (hstsPreload) {
                hsts.append("; preload");
            }
            headers.set("Strict-Transport-Security", hsts.toString());
        }

        rc.next();
    }
}
