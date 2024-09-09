package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.C;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.EMPTY;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.HASKELL;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.KOTLIN;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.PYTHON;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.RUST;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.SWIFT;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.FACT;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.GCC;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.GRADLE_GRADLE;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.MAVEN_BLACKBOX;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.MAVEN_MAVEN;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN_GRADLE;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN_MAVEN;

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
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, true, true, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN, MAVEN_BLACKBOX), true, false));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, true, false, true, true, false, List.of(), true, false));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, false, true, false, false, List.of(), false, false));
        // Jenkins is not supporting XCODE at the moment
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, true, true, true, false, List.of(PLAIN), false, false));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, false, true, false, false, List.of(FACT, GCC), false, false));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, false, false, false, false, true, List.of(), false, false));
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, false, false, false, false, List.of(), false, false));
    }
}
