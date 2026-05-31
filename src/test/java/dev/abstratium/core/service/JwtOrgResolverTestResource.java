package dev.abstratium.core.service;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only JAX-RS resource used by JwtOrgResolverIntegrationTest to exercise
 * JwtOrgResolver.resolveTenantId() via @QuarkusTest HTTP requests.
 */
@Path("/api/test/jwt-org")
public class JwtOrgResolverTestResource {

    @Inject
    @PersistenceUnitExtension
    TenantResolver jwtOrgResolver;

    @GET
    @RolesAllowed("jwt-test-user")
    @Produces(MediaType.APPLICATION_JSON)
    public String resolvedOrgId() {
        return "\"" + jwtOrgResolver.resolveTenantId() + "\"";
    }
}
