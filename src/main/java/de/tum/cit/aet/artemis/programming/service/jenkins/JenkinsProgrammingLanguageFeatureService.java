package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_STATELESS_JENKINS;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.BASH;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.C;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.C_PLUS_PLUS;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.C_SHARP;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.DART;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.EMPTY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.GO;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.HASKELL;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVASCRIPT;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.KOTLIN;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.PYTHON;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.R;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUBY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUST;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.SWIFT;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.TYPESCRIPT;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.FACT;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.GCC;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.GRADLE_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_BLACKBOX;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_MAVEN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_GRADLE;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_MAVEN;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.service.LicenseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;

@Lazy
@Service
@Profile({ PROFILE_JENKINS, PROFILE_STATELESS_JENKINS })
public class JenkinsProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    protected JenkinsProgrammingLanguageFeatureService(LicenseService licenseService) {
        super(licenseService);
    }

    @Override
    protected Map<ProgrammingLanguage, ProgrammingLanguageFeature> getSupportedProgrammingLanguageFeatures() {
        // Must be extended once a new programming language is added
        EnumMap<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new EnumMap<>(ProgrammingLanguage.class);
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false));
        programmingLanguageFeatures.put(BASH, new ProgrammingLanguageFeature(BASH, false, false, false, false, false, List.of(), false));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, false, true, false, false, List.of(FACT, GCC), false));
        programmingLanguageFeatures.put(C_PLUS_PLUS, new ProgrammingLanguageFeature(C_PLUS_PLUS, false, false, true, false, false, List.of(), false));
        programmingLanguageFeatures.put(C_SHARP, new ProgrammingLanguageFeature(C_SHARP, false, false, true, false, false, List.of(), false));
        programmingLanguageFeatures.put(DART, new ProgrammingLanguageFeature(DART, false, false, false, true, false, List.of(), false));
        programmingLanguageFeatures.put(GO, new ProgrammingLanguageFeature(GO, false, false, true, true, false, List.of(), false));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, false, false, false, false, true, List.of(), false));
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, true, true, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN, MAVEN_BLACKBOX), false));
        programmingLanguageFeatures.put(JAVASCRIPT, new ProgrammingLanguageFeature(JAVASCRIPT, false, false, true, false, false, List.of(), false));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, true, false, true, true, false, List.of(), false));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, false, true, false, false, List.of(), false));
        programmingLanguageFeatures.put(R, new ProgrammingLanguageFeature(R, false, false, true, false, false, List.of(), false));
        programmingLanguageFeatures.put(RUBY, new ProgrammingLanguageFeature(RUBY, false, false, false, false, false, List.of(), false));
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, false, true, false, false, List.of(), false));
        // Jenkins is not supporting XCODE at the moment
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, true, true, true, false, List.of(PLAIN), false));
        programmingLanguageFeatures.put(TYPESCRIPT, new ProgrammingLanguageFeature(TYPESCRIPT, false, false, true, false, false, List.of(), false));
        return programmingLanguageFeatures;
    }
}
