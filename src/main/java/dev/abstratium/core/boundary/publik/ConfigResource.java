package dev.abstratium.core.boundary.publik;

import dev.abstratium.core.BuildInfo;
import dev.abstratium.core.entity.Config;
import dev.abstratium.core.service.ConfigService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/public/config")
@Tag(name = "API", description = "Public API endpoints")
public class ConfigResource {

    @Inject
    ConfigService configService;

    @ConfigProperty(name = "client.log.level")
    String clientLogLevel;

    @ConfigProperty(name = "warning.message", defaultValue = "-")
    String warningMessage;

    @ConfigProperty(name = "warning.background.color", defaultValue = "#fff3cd")
    String warningBgColor;

    @ConfigProperty(name = "brand.logo.url", defaultValue = "https://abstratium.dev/abstratium-logo-small.png")
    String brandLogoUrl;

    @ConfigProperty(name = "brand.logo.alt", defaultValue = "Abstratium Logo")
    String brandLogoAlt;

    @ConfigProperty(name = "brand.name", defaultValue = "ABSTRATIUM")
    String brandName;

    @ConfigProperty(name = "abstratium.stage", defaultValue = "dev")
    String stage;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SuccessResponse config() {
        Config dbConfig = configService.getOrCreate();
        return new SuccessResponse(clientLogLevel, BuildInfo.BUILD_TIMESTAMP, warningMessage, warningBgColor, brandLogoUrl, brandLogoAlt, brandName, stage, dbConfig.getCurrencyCode(), dbConfig.getLocale());
    }

    @RegisterForReflection
    public static class SuccessResponse {
        public String logLevel;
        public String baselineBuildTimestamp;
        public String warningMessage;
        public String warningBgColor;
        public String brandLogoUrl;
        public String brandLogoAlt;
        public String brandName;
        public String stage;
        public String currencyCode;
        public String locale;

        public SuccessResponse(String logLevel, String baselineBuildTimestamp, String warningMessage, String warningBgColor, String brandLogoUrl, String brandLogoAlt, String brandName, String stage, String currencyCode, String locale) {
            this.logLevel = logLevel;
            this.baselineBuildTimestamp = baselineBuildTimestamp;
            this.warningMessage = warningMessage;
            this.warningBgColor = warningBgColor;
            this.brandLogoUrl = brandLogoUrl;
            this.brandLogoAlt = brandLogoAlt;
            this.brandName = brandName;
            this.stage = stage;
            this.currencyCode = currencyCode;
            this.locale = locale;
        }
    }
}
