package de.tum.in.www1.artemis.service.programming;

import java.util.Map;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * This service provides information about features the different ProgrammingLanguages support.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureContributor} exposes them.
 */
public interface ProgrammingLanguageFeatureService {

    /**
     * Get the ProgrammingLanguageFeature configured for the given ProgrammingLanguage.
     * @param programmingLanguage for which the ProgrammingLanguageFeature should be returned
     * @return the ProgrammingLanguageFeature for the requested ProgrammingLanguage
     * @throws IllegalArgumentException if no ProgrammingLanguageFeature for the specified ProgrammingLanguage could be found
     */
    ProgrammingLanguageFeature getProgrammingLanguageFeatures(ProgrammingLanguage programmingLanguage) throws IllegalArgumentException;

    Map<ProgrammingLanguage, ProgrammingLanguageFeature> getProgrammingLanguageFeatures();
}
