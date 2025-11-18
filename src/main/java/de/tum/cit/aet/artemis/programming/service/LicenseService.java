package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.LicenseConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Provides licensing information for proprietary software to build jobs via environment variables.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class LicenseService {

    private final LicenseConfiguration licenseConfiguration;

    public LicenseService(LicenseConfiguration licenseConfiguration) {
        this.licenseConfiguration = licenseConfiguration;
    }

    /**
     * Checks whether a required license is configured for the specified exercise type.
     * If no license is required this returns true.
     *
     * @param programmingLanguage the programming language of the exercise type
     * @param projectType         the project type of the exercise type
     * @return whether a required license is configured
     */
    public boolean isLicensed(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType) {
        if (programmingLanguage == ProgrammingLanguage.MATLAB && projectType == null) {
            return licenseConfiguration.getMatlabLicenseServer() != null;
        }

        return true;
    }

    /**
     * Returns environment variables required to run programming exercise tests.
     *
     * @param programmingLanguage the programming language of the exercise
     * @param projectType         the project type of the exercise
     * @return environment variables for the specified exercise type
     */
    public Map<String, String> getEnvironment(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType) {
        if (programmingLanguage == ProgrammingLanguage.MATLAB && projectType == null) {
            return Map.of("MLM_LICENSE_FILE", Objects.requireNonNull(licenseConfiguration.getMatlabLicenseServer()));
        }

        return Map.of();
    }
}
