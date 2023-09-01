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
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, true, false));
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, true, true, true, true, false,
                List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN, MAVEN_BLACKBOX), true, true, false));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, true, false, true, true, false, List.of(), true, true, false));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, false, true, false, false, List.of(), false, true, false));
        // Jenkins is not supporting XCODE at the moment
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, true, true, true, false, List.of(PLAIN), false, true, false));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, false, true, false, false, List.of(FACT, GCC), false, true, false));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, false, false, false, false, true, List.of(), false, true, false));
    }
}
