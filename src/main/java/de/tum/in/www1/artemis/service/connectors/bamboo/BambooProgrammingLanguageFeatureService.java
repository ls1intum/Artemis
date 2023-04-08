package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("bamboo")
public class BambooProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public BambooProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, true, true));
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, true, true, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN), true, true, true));
        programmingLanguageFeatures.put(PYTHON, new ProgrammingLanguageFeature(PYTHON, true, false, true, false, false, List.of(), false, true, true));
        programmingLanguageFeatures.put(C, new ProgrammingLanguageFeature(C, false, true, true, false, false, List.of(FACT, GCC), false, true, true));
        programmingLanguageFeatures.put(HASKELL, new ProgrammingLanguageFeature(HASKELL, true, false, false, false, true, List.of(), false, true, true));
        programmingLanguageFeatures.put(KOTLIN, new ProgrammingLanguageFeature(KOTLIN, true, false, true, true, false, List.of(), true, true, true));
        programmingLanguageFeatures.put(VHDL, new ProgrammingLanguageFeature(VHDL, false, false, false, false, false, List.of(), false, true, true));
        programmingLanguageFeatures.put(ASSEMBLER, new ProgrammingLanguageFeature(ASSEMBLER, false, false, false, false, false, List.of(), false, true, true));
        programmingLanguageFeatures.put(SWIFT, new ProgrammingLanguageFeature(SWIFT, false, true, true, true, false, List.of(PLAIN, XCODE), false, true, true));
        programmingLanguageFeatures.put(OCAML, new ProgrammingLanguageFeature(OCAML, false, false, false, false, true, List.of(), false, true, true));
    }
}
