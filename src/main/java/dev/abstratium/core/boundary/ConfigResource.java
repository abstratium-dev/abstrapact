package dev.abstratium.core.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.core.entity.Config;
import dev.abstratium.core.service.ConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/config")
@Tag(name = "Config", description = "Organisation configuration management")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigResource {

    @Inject
    ConfigService configService;

    @GET
    @Operation(summary = "Get organisation configuration")
    public Response getConfig() {
        Config config = configService.getOrCreate();
        return Response.ok(config).build();
    }

    @PUT
    @Operation(summary = "Update organisation configuration")
    public Response update(UpdateRequest request) {
        if (request == null || request.currencyCode == null || request.currencyCode.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("currencyCode is required").build();
        }
        if (request.locale == null || request.locale.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("locale is required").build();
        }
        // Ensure config exists before updating (lazy creation from env defaults)
        configService.getOrCreate();
        Config updated = configService.update(request.currencyCode.trim(), request.locale.trim());
        return Response.ok(updated).build();
    }

    public static class UpdateRequest {
        public String currencyCode;
        public String locale;
    }
}
