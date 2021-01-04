package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("jenkins")
public class JenkinsProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public JenkinsProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, true, true, true, false, List.of(ECLIPSE, MAVEN)));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, true, false, true, false, false, List.of()));
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, true, false, true, false, List.of()));
        // TODO: Should be re-enabled once Jenkins Pipelines are used
        // programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, false, true, false, false));
        // programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, false, false, false, false, false));
    }
}
