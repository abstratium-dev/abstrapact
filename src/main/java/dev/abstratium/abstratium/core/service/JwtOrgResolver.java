package dev.abstratium.abstratium.core.service;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.Set;

/**
 * Tenant resolver for discriminator-based multitenancy.
 * Extracts the {@code orgId} claim from the {@code Authorization: Bearer <jwt>} header.
 * Falls back to the default org when no valid Bearer token is present (e.g. public endpoints,
 * OAuth2 token exchange, sign-in flow, or test contexts).
 */
@PersistenceUnitExtension
@ApplicationScoped
public class JwtOrgResolver implements TenantResolver {

    private static final Logger log = Logger.getLogger(JwtOrgResolver.class);

    // Default organisation ID from V01.021__migrate_existing_data_to_default_org.sql
    public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_ORG_ID;
    }

    @Override
    public String resolveTenantId() {
        log.debug("Resolving tenant ID...");

        // Try to get the current HTTP request from CDI
        HttpServerRequest request = getCurrentRequest();

        // If no request context is available (e.g., in tests), return default org
        if (request == null) {
            log.debug("No request context available, returning default org: " + DEFAULT_ORG_ID);
            return DEFAULT_ORG_ID;
        }

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("No Bearer token found, returning default org: " + DEFAULT_ORG_ID);
                return DEFAULT_ORG_ID;
            }

            String token = authHeader.substring(7);
            String orgId = extractOrgIdFromJwt(token);
            if (orgId != null && !orgId.isBlank()) {
                log.debug("Resolved orgId from JWT: " + orgId);
                return orgId;
            }
        } catch (Exception e) {
            log.debug("Could not resolve orgId from JWT, using default", e);
        }
        log.debug("Falling back to default org: " + DEFAULT_ORG_ID);
        return DEFAULT_ORG_ID;
    }

    private HttpServerRequest getCurrentRequest() {
        try {
            BeanManager beanManager = CDI.current().getBeanManager();
            Set<Bean<?>> beans = beanManager.getBeans(HttpServerRequest.class);
            if (beans.isEmpty()) {
                return null;
            }
            Bean<?> bean = beans.iterator().next();
            CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
            return (HttpServerRequest) beanManager.getReference(bean, HttpServerRequest.class, ctx);
        } catch (Exception e) {
            // No request context available (e.g., in tests without HTTP request)
            return null;
        }
    }

    private String extractOrgIdFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            int start = payload.indexOf("\"orgId\":\"");
            if (start == -1) {
                return null;
            }
            start += 9;
            int end = payload.indexOf("\"", start);
            if (end == -1) {
                return null;
            }
            return payload.substring(start, end);
        } catch (Exception e) {
            log.debug("Failed to extract orgId from JWT payload", e);
            return null;
        }
    }
}
