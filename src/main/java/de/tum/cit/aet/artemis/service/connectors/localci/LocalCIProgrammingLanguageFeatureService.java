package de.tum.cit.aet.artemis.service.connectors.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.ASSEMBLER;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.C;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.HASKELL;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.KOTLIN;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.OCAML;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.PYTHON;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.RUST;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.SWIFT;
import static de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage.VHDL;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.FACT;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.GCC;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.GRADLE_GRADLE;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.MAVEN_MAVEN;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.PLAIN;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.PLAIN_GRADLE;
import static de.tum.cit.aet.artemis.domain.enumeration.ProjectType.PLAIN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeatureService;

/**
 * Sets the features provided for the different programming languages when using the local CI system.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public LocalCIProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        // TODO LOCALVC_CI: Local CI is not supporting EMPTY at the moment.
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, true, true, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN), false, true));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, false, true, false, false, List.of(), false, true));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, true, true, false, false, List.of(FACT, GCC), false, true));
        programmingLanguageFeatures.put(ASSEMBLER, new ProgrammingLanguageFeature(ASSEMBLER, false, false, false, false, false, List.of(), false, true));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, false, false, true, true, false, List.of(), false, true));
        programmingLanguageFeatures.put(VHDL, new ProgrammingLanguageFeature(VHDL, false, false, false, false, false, List.of(), false, true));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, true, false, false, false, true, List.of(), false, true));
        programmingLanguageFeatures.put(OCAML, new ProgrammingLanguageFeature(OCAML, false, false, false, false, true, List.of(), false, true));
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, false, true, true, false, List.of(PLAIN), false, true));
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, false, false, false, false, List.of(), false, true));
    }
}
