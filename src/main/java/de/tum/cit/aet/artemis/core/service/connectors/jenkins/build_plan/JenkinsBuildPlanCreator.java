package de.tum.cit.aet.artemis.core.service.connectors.jenkins.build_plan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationBuildPlanException;
import de.tum.cit.aet.artemis.core.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.core.service.connectors.jenkins.JenkinsXmlConfigBuilder;
import de.tum.cit.aet.artemis.core.service.connectors.jenkins.JenkinsXmlFileUtils;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.service.ResourceLoaderService;

@Profile("jenkins")
@Component
public class JenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

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
    private String artemisServerUrl;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    private String artemisAuthenticationTokenKey;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final ResourceLoaderService resourceLoaderService;

    public JenkinsBuildPlanCreator(final ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = artemisServerUrl + Constants.NEW_RESULT_RESOURCE_API_PATH;
    }

    @Override
    public Document buildBasicConfig(final ProgrammingLanguage programmingLanguage, final Optional<ProjectType> projectType,
            final InternalVcsRepositoryURLs internalVcsRepositoryURLs, final boolean checkoutSolution, final String buildPlanUrl) {
        final String jenkinsfile = getJenkinsfile(internalVcsRepositoryURLs, programmingLanguage, checkoutSolution, buildPlanUrl);

        final Path configFilePath = Path.of("templates", "jenkins", "config.xml");
        final var configFileReplacements = Map.of(REPLACE_PIPELINE_SCRIPT, jenkinsfile, REPLACE_PUSH_TOKEN, pushToken);
        final var xmlResource = resourceLoaderService.getResource(configFilePath);
        return JenkinsXmlFileUtils.readXmlFile(xmlResource, configFileReplacements);
    }

    private String getJenkinsfile(final InternalVcsRepositoryURLs internalVcsRepositoryURLs, final ProgrammingLanguage programmingLanguage, final boolean checkoutSolution,
            final String buildPlanUrl) {
        final String jenkinsfile = makeSafeForXml(loadJenkinsfile());
        final var replacements = getReplacements(internalVcsRepositoryURLs, programmingLanguage, checkoutSolution, buildPlanUrl);

        return replacePipelineScriptParameters(jenkinsfile, replacements);
    }

    private String loadJenkinsfile() {
        final Resource resource = resourceLoaderService.getResource(Path.of("templates", "jenkins", "Jenkinsfile"));

        try (InputStream inputStream = resource.getInputStream()) {
            return IOUtils.toString(inputStream, Charset.defaultCharset());
        }
        catch (IOException e) {
            throw new ContinuousIntegrationBuildPlanException("Could not load Jenkinsfile.", e);
        }
    }

    /**
     * XML uses special characters for its syntax. If the content of XML file also contains those characters, they have
     * to be escaped. That way, they aren't confused with the structural meaning of the XML characters.
     *
     * @param script The raw script containing unescaped characters.
     * @return The script with escaped XML characters.
     */
    private String makeSafeForXml(final String script) {
        String result = script;
        result = result.replace("'", "&apos;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        return result.replace("\\", "\\\\");
    }

    private Map<String, String> getReplacements(final InternalVcsRepositoryURLs internalVcsRepositoryURLs, final ProgrammingLanguage programmingLanguage,
            final boolean checkoutSolution, final String buildPlanUrl) {
        final Map<String, String> replacements = new HashMap<>();

        replacements.put(REPLACE_TEST_REPO, internalVcsRepositoryURLs.testRepositoryUri().getURI().toString());
        replacements.put(REPLACE_ASSIGNMENT_REPO, internalVcsRepositoryURLs.assignmentRepositoryUri().getURI().toString());
        replacements.put(REPLACE_SOLUTION_REPO, internalVcsRepositoryURLs.solutionRepositoryUri().getURI().toString());
        replacements.put(REPLACE_GIT_CREDENTIALS, gitCredentialsKey);
        replacements.put(REPLACE_ASSIGNMENT_CHECKOUT_PATH, ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage));
        replacements.put(REPLACE_TESTS_CHECKOUT_PATH, ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage));
        if (checkoutSolution) {
            replacements.put(REPLACE_SOLUTION_CHECKOUT_PATH, ContinuousIntegrationService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage));
        }
        replacements.put(REPLACE_ARTEMIS_NOTIFICATION_URL, artemisNotificationUrl);
        replacements.put(REPLACE_NOTIFICATIONS_TOKEN, artemisAuthenticationTokenKey);
        replacements.put(REPLACE_JENKINS_TIMEOUT, buildTimeout);
        replacements.put(REPLACE_DEFAULT_BRANCH, defaultBranch);
        replacements.put(REPLACE_CHECKOUT_SOLUTION, Boolean.toString(checkoutSolution));
        replacements.put(REPLACE_BUILD_PLAN_URL, buildPlanUrl);

        return replacements;
    }

    private String replacePipelineScriptParameters(final String jenkinsfile, final Map<String, String> variablesToReplace) {
        String updatedJenkinsfile = jenkinsfile;

        for (final var replacement : variablesToReplace.entrySet()) {
            updatedJenkinsfile = updatedJenkinsfile.replace(replacement.getKey(), replacement.getValue());
        }

        return updatedJenkinsfile;
    }
}
