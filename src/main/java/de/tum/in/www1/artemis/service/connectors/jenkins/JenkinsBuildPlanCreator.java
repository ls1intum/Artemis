package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Component
public class JenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(JenkinsBuildPlanCreator.class);

    private static final String REPLACE_PIPELINE_SCRIPT = "#pipelineScript";

    private static final String REPLACE_TEST_REPO = "#testRepository";

    private static final String REPLACE_ASSIGNMENT_REPO = "#assignmentRepository";

    private static final String REPLACE_SOLUTION_REPO = "#solutionRepository";

    private static final String REPLACE_GIT_CREDENTIALS = "#gitCredentials";

    private static final String REPLACE_ASSIGNMENT_CHECKOUT_PATH = "#assignmentCheckoutPath";

    private static final String REPLACE_TESTS_CHECKOUT_PATH = "#testsCheckoutPath";

    private static final String REPLACE_SOLUTION_CHECKOUT_PATH = "#solutionCheckoutPath";

    private static final String REPLACE_PUSH_TOKEN = "#secretPushToken";

    private static final String REPLACE_ARTEMIS_NOTIFICATION_URL = "#notificationsUrl";

    private static final String REPLACE_NOTIFICATIONS_TOKEN = "#jenkinsNotificationToken";

    private static final String REPLACE_JENKINS_TIMEOUT = "#jenkinsTimeout";

    private static final String REPLACE_DEFAULT_BRANCH = "#defaultBranch";

    private static final String REPLACE_CHECKOUT_SOLUTION = "#checkoutSolution";

    private static final String REPLACE_BUILD_PLAN_URL = "#buildPlanUrl";

    private String artemisNotificationUrl;

    @Value("${artemis.continuous-integration.secret-push-token}")
    private String pushToken;

    @Value("${artemis.continuous-integration.vcs-credentials}")
    private String gitCredentialsKey;

    @Value("${artemis.continuous-integration.build-timeout:#{30}}")
    private String buildTimeout;

    @Value("${server.url}")
    private String ARTEMIS_SERVER_URL;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_KEY;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final ResourceLoaderService resourceLoaderService;

    public JenkinsBuildPlanCreator(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_SERVER_URL + Constants.NEW_RESULT_RESOURCE_API_PATH;
    }

    private Map<String, String> getReplacements(InternalVcsRepositoryURLs internalVcsRepositoryURLs, boolean checkoutSolution, String buildPlanUrl) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(REPLACE_TEST_REPO, internalVcsRepositoryURLs.testRepositoryUrl().getURI().toString());
        replacements.put(REPLACE_ASSIGNMENT_REPO, internalVcsRepositoryURLs.assignmentRepositoryUrl().getURI().toString());
        replacements.put(REPLACE_SOLUTION_REPO, internalVcsRepositoryURLs.solutionRepositoryUrl().getURI().toString());
        replacements.put(REPLACE_GIT_CREDENTIALS, gitCredentialsKey);
        replacements.put(REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH);
        replacements.put(REPLACE_TESTS_CHECKOUT_PATH, Constants.TESTS_CHECKOUT_PATH);
        replacements.put(REPLACE_SOLUTION_CHECKOUT_PATH, Constants.SOLUTION_CHECKOUT_PATH);
        replacements.put(REPLACE_ARTEMIS_NOTIFICATION_URL, artemisNotificationUrl);
        replacements.put(REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY);
        replacements.put(REPLACE_JENKINS_TIMEOUT, buildTimeout);
        replacements.put(REPLACE_DEFAULT_BRANCH, defaultBranch);
        replacements.put(REPLACE_CHECKOUT_SOLUTION, Boolean.toString(checkoutSolution));
        replacements.put(REPLACE_BUILD_PLAN_URL, buildPlanUrl);

        return replacements;
    }

    @Override
    public Document buildBasicConfig(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs,
            boolean checkoutSolution, String buildPlanUrl) {
        final var resourcePath = Path.of("templates", "jenkins", "config.xml");
        Resource resource = resourceLoaderService.getResource("templates", "jenkins", "Jenkinsfile");
        String jenkinsFileScript;
        try {
            jenkinsFileScript = Files.readString(resource.getFile().toPath());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        var replacements = getReplacements(internalVcsRepositoryURLs, checkoutSolution, buildPlanUrl);
        jenkinsFileScript = jenkinsFileScript.replace("'", "&apos;");
        jenkinsFileScript = jenkinsFileScript.replace("<", "&lt;");
        jenkinsFileScript = jenkinsFileScript.replace(">", "&gt;");
        jenkinsFileScript = jenkinsFileScript.replace("\\", "\\\\");
        jenkinsFileScript = replacePipelineScriptParameters(jenkinsFileScript, replacements);

        replacements = Map.of(REPLACE_PIPELINE_SCRIPT, jenkinsFileScript, REPLACE_PUSH_TOKEN, pushToken);

        final var xmlResource = resourceLoaderService.getResource(resourcePath.toString());
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    private String replacePipelineScriptParameters(String jenkinsFileScript, Map<String, String> variablesToReplace) {
        if (variablesToReplace != null) {
            for (final var replacement : variablesToReplace.entrySet()) {
                jenkinsFileScript = jenkinsFileScript.replace(replacement.getKey(), replacement.getValue());
            }
        }
        return jenkinsFileScript;
    }
}
