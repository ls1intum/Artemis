package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

@Profile(PROFILE_CORE)
@ConfigurationProperties(prefix = "artemis.licenses")
public class LicenseConfiguration {

    private final MatLabLicense matlab;

    public record MatLabLicense(String licenseServer) {
    }

    public LicenseConfiguration(MatLabLicense matlab) {
        this.matlab = matlab;
    }

    @Nullable
    public String getMatlabLicenseServer() {
        if (matlab == null) {
            return null;
        }
        return matlab.licenseServer();
    }
}
