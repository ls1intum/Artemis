package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.C;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.EMPTY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.HASKELL;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVASCRIPT;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.KOTLIN;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.PYTHON;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.R;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUST;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.SWIFT;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.FACT;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.GCC;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.GRADLE_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_BLACKBOX;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_MAVEN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;

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
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, false, true, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(JAVASCRIPT, new ProgrammingLanguageFeature(JAVASCRIPT, false, false, true, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(R, new ProgrammingLanguageFeature(R, false, false, true, false, false, List.of(), false, false));
    }
}
