package de.tum.in.www1.artemis.service.connectors.gitlabci;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.EMPTY;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVASCRIPT;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.MAVEN_MAVEN;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.PLAIN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("gitlabci")
public class GitLabCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public GitLabCIProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, false, false, true, false, List.of(PLAIN_MAVEN, MAVEN_MAVEN), false, false));
        programmingLanguageFeatures.put(JAVASCRIPT, new ProgrammingLanguageFeature(JAVASCRIPT, false, false, false, false, false, List.of(), false, false));
    }
}
