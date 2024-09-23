package de.tum.cit.aet.artemis.programming.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * This service provides information about features the different ProgrammingLanguages support.
 * The configuration is also available in the client as this class exposes them.
 */
public abstract class ProgrammingLanguageFeatureService implements InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingLanguageFeatureService.class);

    protected final Map<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new HashMap<>();

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

    public Map<ProgrammingLanguage, ProgrammingLanguageFeature> getProgrammingLanguageFeatures() {
        return programmingLanguageFeatures;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("programmingLanguageFeatures", getProgrammingLanguageFeatures().values());
    }
}
