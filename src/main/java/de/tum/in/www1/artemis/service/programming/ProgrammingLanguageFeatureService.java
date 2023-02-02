package de.tum.in.www1.artemis.service.programming;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * This service provides information about features the different ProgrammingLanguages support.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureContributor} exposes them.
 */
public abstract class ProgrammingLanguageFeatureService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingLanguageFeatureService.class);

    protected final Map<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new HashMap<>();

    /**
     * Get the ProgrammingLanguageFeature configured for the given ProgrammingLanguage.
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

    public Set<ProgrammingLanguage> getSupportedLanguages() {
        return programmingLanguageFeatures.keySet();
    }

    public Map<ProgrammingLanguage, ProgrammingLanguageFeature> getProgrammingLanguageFeatures() {
        return programmingLanguageFeatures;
    }
}
