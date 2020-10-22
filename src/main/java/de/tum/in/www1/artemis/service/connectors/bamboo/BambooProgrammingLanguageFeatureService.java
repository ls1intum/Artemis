package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("bamboo")
public class BambooProgrammingLanguageFeatureService implements ProgrammingLanguageFeatureService {

    private final Logger log = LoggerFactory.getLogger(BambooProgrammingLanguageFeatureService.class);

    private final Map<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new HashMap<>();

    public BambooProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(ProgrammingLanguage.JAVA, new ProgrammingLanguageFeature(ProgrammingLanguage.JAVA, true, true, true, true, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.PYTHON, new ProgrammingLanguageFeature(ProgrammingLanguage.PYTHON, true, false, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.C, new ProgrammingLanguageFeature(ProgrammingLanguage.C, false, false, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.HASKELL, new ProgrammingLanguageFeature(ProgrammingLanguage.HASKELL, true, true, false, false, true));
        programmingLanguageFeatures.put(ProgrammingLanguage.KOTLIN, new ProgrammingLanguageFeature(ProgrammingLanguage.KOTLIN, true, false, false, true, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.VHDL, new ProgrammingLanguageFeature(ProgrammingLanguage.VHDL, false, false, false, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.ASSEMBLER, new ProgrammingLanguageFeature(ProgrammingLanguage.ASSEMBLER, false, false, false, false, false));
    }

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
