package de.tum.in.www1.artemis.service.connectors.gitlabci;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("gitlabci")
public class GitLabCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public GitLabCIProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of()));
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, false, false, true, false, List.of(PLAIN_MAVEN, MAVEN_MAVEN)));
    }
}
