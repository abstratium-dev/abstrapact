package dev.abstratium.core.filter;

import dev.abstratium.core.service.CurrentOrgContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Test-only JAX-RS resource used by OrgIdResolutionFilterTest to exercise
 * the filter's orgId resolution via HTTP requests.
 */
@Path("/api/test/org-id")
public class OrgIdResolutionFilterTestResource {

    @Inject
    CurrentOrgContext currentOrgContext;

    @GET
    @RolesAllowed("jwt-test-user")
    @Produces(MediaType.TEXT_PLAIN)
    public String getOrgId() {
        String orgId = currentOrgContext.getOrgId();
        return orgId != null ? orgId : "null";
    }
}
