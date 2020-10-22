package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Profile("jenkins")
@Component
public class DefaultJenkinsBuildPlanCreator extends AbstractJenkinsBuildPlanCreator {

    public DefaultJenkinsBuildPlanCreator(ResourceLoader resourceLoader, @Lazy JenkinsService jenkinsService) {
        super(resourceLoader, jenkinsService);
    }

    @Override
    public String getPipelineScript(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        final var resourcePath = Path.of("templates", "jenkins", programmingLanguage.name().toLowerCase(), "Jenkinsfile");

        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_TESTS_CHECKOUT_PATH, Constants.TESTS_CHECKOUT_PATH,
                REPLACE_ARTEMIS_NOTIFICATION_URL, artemisNotificationUrl, REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY, REPLACE_DOCKER_IMAGE_NAME,
                jenkinsService.getDockerImageName(ProgrammingLanguage.C));

        return replacePipelineScriptParameters(resourcePath, replacements);
    }
}
