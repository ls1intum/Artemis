package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.LicenseConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

@Profile(PROFILE_CORE)
@Service
public class LicenseService {

    private final LicenseConfiguration licenseConfiguration;

    public LicenseService(LicenseConfiguration licenseConfiguration) {
        this.licenseConfiguration = licenseConfiguration;
    }

    public boolean isLicensed(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType) {
        if (programmingLanguage == ProgrammingLanguage.MATLAB && projectType == null) {
            return licenseConfiguration.getMatlabLicenseServer() != null;
        }

        return true;
    }

    public Map<String, String> getEnvironment(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType) {
        if (programmingLanguage == ProgrammingLanguage.MATLAB && projectType == null) {
            return Map.of("MLM_LICENSE_FILE", Objects.requireNonNull(licenseConfiguration.getMatlabLicenseServer()));
        }

        return Map.of();
    }
}
