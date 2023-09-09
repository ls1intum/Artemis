package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

/**
 * Sets the features provided for the different programming languages when using the local CI system.
 */
@Service
@Profile("localci")
public class LocalCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public LocalCIProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        // TODO LOCALVC_CI: Local CI is not supporting EMPTY at the moment.
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, false, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE), false, false, true));
        // TODO LOCALVC_CI: Local CI is not supporting Python at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting C at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting Haskell at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting Kotlin at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting VHDL at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting Assembler at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting Swift at the moment.
        // TODO LOCALVC_CI: Local CI is not supporting OCAML at the moment.
    }
}
