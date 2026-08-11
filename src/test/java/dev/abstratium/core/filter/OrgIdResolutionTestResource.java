package dev.abstratium.core.filter;

import dev.abstratium.core.service.CurrentOrgContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/test")
public class OrgIdResolutionTestResource {

    @Inject
    CurrentOrgContext currentOrgContext;

    @GET
    @Path("/org-id")
    @Produces(MediaType.TEXT_PLAIN)
    public String orgId() {
        return String.valueOf(currentOrgContext.getOrgId());
    }
}
