package de.tum.in.www1.artemis.service.programming;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * This service provides information about features the different ProgrammingLanguages support.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureContributor} exposes them.
 */
@Service
public class ProgrammingLanguageFeatureService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingLanguageFeatureService.class);

    private final Map<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new HashMap<>();

    public ProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(ProgrammingLanguage.JAVA, new ProgrammingLanguageFeature(ProgrammingLanguage.JAVA, true, true, true, true, true, true, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.PYTHON, new ProgrammingLanguageFeature(ProgrammingLanguage.PYTHON, true, false, true, true, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.C, new ProgrammingLanguageFeature(ProgrammingLanguage.C, false, false, true, true, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.HASKELL, new ProgrammingLanguageFeature(ProgrammingLanguage.HASKELL, true, false, true, true, false, false, true));
        programmingLanguageFeatures.put(ProgrammingLanguage.KOTLIN, new ProgrammingLanguageFeature(ProgrammingLanguage.KOTLIN, true, false, true, false, false, true, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.VHDL, new ProgrammingLanguageFeature(ProgrammingLanguage.VHDL, false, false, true, false, false, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.ASSEMBLER,
                new ProgrammingLanguageFeature(ProgrammingLanguage.ASSEMBLER, false, false, true, false, false, false, false));
    }

    /**
     * Get the ProgrammingLanguageFeature configured for the given ProgrammingLanguage.
     * @param programmingLanguage for which the ProgrammingLanguageFeature should be returned
     * @return the ProgrammingLanguageFeature for the requested ProgrammingLanguage
     * @throws IllegalArgumentException if no ProgrammingLanguageFeature for the specified ProgrammingLanguage could be found
     */
    public ProgrammingLanguageFeature getProgrammingLanguageFeatures(ProgrammingLanguage programmingLanguage) throws IllegalArgumentException {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatures.get(programmingLanguage);

        if (programmingLanguageFeature == null) {
            log.error("ProgrammingLanguage " + programmingLanguage + " does not have ProgrammingLanguageFeature");
            throw new IllegalArgumentException("ProgrammingLanguage " + programmingLanguage + " does not have ProgrammingLanguageFeature");
        }
        return programmingLanguageFeature;
    }

    public Map<ProgrammingLanguage, ProgrammingLanguageFeature> getProgrammingLanguageFeatures() {
        return programmingLanguageFeatures;
    }
}
