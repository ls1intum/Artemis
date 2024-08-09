package de.tum.in.www1.artemis.service.connectors.hades;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_HADES;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.ASSEMBLER;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.C;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.HASKELL;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.KOTLIN;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.OCAML;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.PYTHON;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.SWIFT;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.VHDL;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.FACT;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.GCC;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.GRADLE_GRADLE;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.MAVEN_MAVEN;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN_GRADLE;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Profile(PROFILE_HADES)
@Service
public class HadesCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public HadesCIProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, false, false, false, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN), false, false));

        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, false, false, true, false, false, List.of(), false, false));

        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, false, true, false, false, List.of(FACT, GCC), false, false));

        programmingLanguageFeatures.put(ASSEMBLER, new ProgrammingLanguageFeature(ASSEMBLER, false, false, false, false, false, List.of(), false, false));

        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, false, false, false, true, false, List.of(), false, false));

        programmingLanguageFeatures.put(VHDL, new ProgrammingLanguageFeature(VHDL, false, false, false, false, false, List.of(), false, false));

        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, true, false, false, false, true, List.of(), false, false));

        programmingLanguageFeatures.put(OCAML, new ProgrammingLanguageFeature(OCAML, false, false, false, false, true, List.of(), false, false));

        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, false, true, true, false, List.of(PLAIN), false, false));
    }
}
