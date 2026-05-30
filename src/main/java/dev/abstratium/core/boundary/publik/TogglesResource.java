package dev.abstratium.core.boundary.publik;

import dev.abstratium.core.service.TogglesService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.Set;

/**
 * REST endpoint for retrieving public toggle/feature flag values.
 * Returns a map of hardcoded toggle names to their evaluated values.
 * This endpoint is public and does not require authentication.
 */
@Path("/public/toggles")
@Tag(name = "API", description = "Public API endpoints")
public class TogglesResource {

    @Inject
    TogglesService togglesService;

    // Hardcoded toggle names for public access
    private static final Set<String> PUBLIC_TOGGLE_NAMES = Set.of(
            "going-down-for-maintenance"
    );

    /**
     * Get values for the hardcoded set of public toggles.
     * Currently includes:
     * - going-down-for-maintenance: empty string if not down, or message with downtime details
     *
     * @return map of toggle names to their values
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getToggles() {
        return togglesService.getToggleValues(PUBLIC_TOGGLE_NAMES, Map.of());
    }
}
