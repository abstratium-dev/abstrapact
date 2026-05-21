package dev.abstratium.demo.boundary.publik;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Public endpoint accessible without authentication.
 * 
 * Returns basic application information that can be displayed to
 * unauthenticated users.
 */
@Path("/public/info")
@PermitAll
public class PublicInfoResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getInfo() {
        return Map.of(
            "application", "Abstracore",
            "version", "1.0.0",
            "description", "A functional component template built with Quarkus and Angular."
        );
    }
}
