package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("jenkins")
public class JenkinsProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public JenkinsProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(ProgrammingLanguage.JAVA, new ProgrammingLanguageFeature(ProgrammingLanguage.JAVA, false, true, true, true, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.PYTHON, new ProgrammingLanguageFeature(ProgrammingLanguage.PYTHON, true, false, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.C, new ProgrammingLanguageFeature(ProgrammingLanguage.C, false, false, true, false, false));
        programmingLanguageFeatures.put(ProgrammingLanguage.HASKELL, new ProgrammingLanguageFeature(ProgrammingLanguage.HASKELL, false, false, false, false, false));
    }
}
