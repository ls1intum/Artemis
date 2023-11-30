package de.tum.in.www1.artemis.service.connectors.hades;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.MAVEN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Profile("hades")
@Service
public class HadesCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public HadesCIProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(JAVA,
                new ProgrammingLanguageFeature(JAVA, false, false, false, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE, PLAIN_MAVEN, MAVEN_MAVEN), false, false, false));
    }
}
