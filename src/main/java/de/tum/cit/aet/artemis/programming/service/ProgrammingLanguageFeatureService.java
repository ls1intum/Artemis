package de.tum.cit.aet.artemis.programming.service;

import java.util.EnumMap;
import java.util.Map;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * This service provides information about features the different ProgrammingLanguages support.
 * The configuration is also available in the client as this class exposes them.
 */
public abstract class ProgrammingLanguageFeatureService implements InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingLanguageFeatureService.class);

    private final LicenseService licenseService;

    private final Map<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures;

    protected ProgrammingLanguageFeatureService(LicenseService licenseService) {
        this.licenseService = licenseService;
        this.programmingLanguageFeatures = getEnabledFeatures();
    }

    protected abstract Map<ProgrammingLanguage, ProgrammingLanguageFeature> getSupportedProgrammingLanguageFeatures();

    /**
     * Get the ProgrammingLanguageFeature configured for the given ProgrammingLanguage.
     *
     * @param programmingLanguage for which the ProgrammingLanguageFeature should be returned
     * @return the ProgrammingLanguageFeature for the requested ProgrammingLanguage
     * @throws IllegalArgumentException if no ProgrammingLanguageFeature for the specified ProgrammingLanguage could be found
     */
    public ProgrammingLanguageFeature getProgrammingLanguageFeatures(ProgrammingLanguage programmingLanguage) throws IllegalArgumentException {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatures.get(programmingLanguage);

        if (programmingLanguageFeature == null) {
            log.error("ProgrammingLanguage {} does not have ProgrammingLanguageFeature", programmingLanguage);
            throw new IllegalArgumentException("ProgrammingLanguage " + programmingLanguage + " does not have ProgrammingLanguageFeature");
        }
        return programmingLanguageFeature;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("programmingLanguageFeatures", programmingLanguageFeatures.values());
    }

    private Map<ProgrammingLanguage, ProgrammingLanguageFeature> getEnabledFeatures() {
        var features = new EnumMap<ProgrammingLanguage, ProgrammingLanguageFeature>(ProgrammingLanguage.class);
        for (var programmingLanguageFeatureEntry : getSupportedProgrammingLanguageFeatures().entrySet()) {
            var language = programmingLanguageFeatureEntry.getKey();
            var feature = programmingLanguageFeatureEntry.getValue();
            if (feature.projectTypes().isEmpty()) {
                if (isProjectTypeUsable(language, null)) {
                    features.put(language, feature);
                }
            }
            else {
                var filteredProjectTypes = feature.projectTypes().stream().filter((projectType -> isProjectTypeUsable(language, projectType))).toList();
                if (!filteredProjectTypes.isEmpty()) {
                    // @formatter:off
                    var filteredFeature = new ProgrammingLanguageFeature(
                        feature.programmingLanguage(),
                        feature.sequentialTestRuns(),
                        feature.staticCodeAnalysis(),
                        feature.plagiarismCheckSupported(),
                        feature.packageNameRequired(),
                        feature.checkoutSolutionRepositoryAllowed(),
                        filteredProjectTypes,
                        feature.auxiliaryRepositoriesSupported()
                    );
                    // @formatter:on
                    features.put(language, filteredFeature);
                }
            }
        }
        return features;
    }

    private boolean isProjectTypeUsable(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType) {
        return licenseService.isLicensed(programmingLanguage, projectType);
    }
}
