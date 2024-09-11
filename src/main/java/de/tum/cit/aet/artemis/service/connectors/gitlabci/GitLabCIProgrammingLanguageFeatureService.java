package de.tum.cit.aet.artemis.service.connectors.gitlabci;

import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.EMPTY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.RUST;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.MAVEN_MAVEN;
import static de.tum.cit.aet.artemis.programming.domain.ProjectType.PLAIN_MAVEN;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeatureService;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Service
@Profile("gitlabci")
public class GitLabCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public GitLabCIProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, false, false, true, false, List.of(PLAIN_MAVEN, MAVEN_MAVEN), false, false));
        programmingLanguageFeatures.put(RUST, new ProgrammingLanguageFeature(RUST, false, false, false, false, false, List.of(), false, false));
    }
}
