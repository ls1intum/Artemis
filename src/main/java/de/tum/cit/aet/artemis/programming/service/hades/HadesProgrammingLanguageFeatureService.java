package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.ASSEMBLER;
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
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.MATLAB;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.OCAML;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.PYTHON;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.R;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUBY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUST;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.SWIFT;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.TYPESCRIPT;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.VHDL;
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

/**
 * Sets the features provided for the different programming languages when using Hades, the exteral CI system.
 * Mirrored from LocalCIProgrammingLanguageFeatureService.
 */
@Lazy
@Service
@Profile({ PROFILE_HADES })
public class HadesProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    protected HadesProgrammingLanguageFeatureService(LicenseService licenseService) {
        super(licenseService);
    }

    @Override
    protected Map<ProgrammingLanguage, ProgrammingLanguageFeature> getSupportedProgrammingLanguageFeatures() {
        // Must be extended once a new programming language is added
        EnumMap<ProgrammingLanguage, ProgrammingLanguageFeature> programmingLanguageFeatures = new EnumMap<>(ProgrammingLanguage.class);
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), true));
        programmingLanguageFeatures.put(ASSEMBLER, new ProgrammingLanguageFeature(ASSEMBLER, false, false, false, false, false, List.of(), true));
        programmingLanguageFeatures.put(BASH, new ProgrammingLanguageFeature(BASH, false, false, false, false, false, List.of(), true));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, true, true, false, false, List.of(FACT, GCC), true));
        programmingLanguageFeatures.put(C_PLUS_PLUS, new ProgrammingLanguageFeature(C_PLUS_PLUS, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(C_SHARP, new ProgrammingLanguageFeature(C_SHARP, false, false, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(DART, new ProgrammingLanguageFeature(DART, false, true, false, true, false, List.of(), true));
        programmingLanguageFeatures.put(GO, new ProgrammingLanguageFeature(GO, false, false, true, true, false, List.of(), true));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, true, false, false, false, true, List.of(), true));
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, true, true, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN, MAVEN_BLACKBOX), true));
        programmingLanguageFeatures.put(JAVASCRIPT, new ProgrammingLanguageFeature(JAVASCRIPT, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, false, false, true, true, false, List.of(), true));
        programmingLanguageFeatures.put(MATLAB, new ProgrammingLanguageFeature(MATLAB, false, false, false, false, false, List.of(), true));
        programmingLanguageFeatures.put(OCAML, new ProgrammingLanguageFeature(OCAML, false, false, false, false, true, List.of(), true));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(R, new ProgrammingLanguageFeature(R, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(RUBY, new ProgrammingLanguageFeature(RUBY, false, true, false, false, false, List.of(), true));
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, false, true, true, false, List.of(PLAIN), true));
        programmingLanguageFeatures.put(TYPESCRIPT, new ProgrammingLanguageFeature(TYPESCRIPT, false, true, true, false, false, List.of(), true));
        programmingLanguageFeatures.put(VHDL, new ProgrammingLanguageFeature(VHDL, false, false, false, false, false, List.of(), true));
        return programmingLanguageFeatures;
    }
}
