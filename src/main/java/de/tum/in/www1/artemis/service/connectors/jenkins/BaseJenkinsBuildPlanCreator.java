package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.ResourceLoaderService;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
public class BaseJenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(BaseJenkinsBuildPlanCreator.class);

    protected static final String REPLACE_PIPELINE_SCRIPT = "#pipelineScript";

    protected static final String REPLACE_TEST_REPO = "#testRepository";

    protected static final String REPLACE_ASSIGNMENT_REPO = "#assignmentRepository";

    protected static final String REPLACE_GIT_CREDENTIALS = "#gitCredentials";

    protected static final String REPLACE_ASSIGNMENT_CHECKOUT_PATH = "#assignmentCheckoutPath";

    protected static final String REPLACE_TESTS_CHECKOUT_PATH = "#testsCheckoutPath";

    protected static final String REPLACE_PUSH_TOKEN = "#secretPushToken";

    protected static final String REPLACE_ARTEMIS_NOTIFICATION_URL = "#notificationsUrl";

    protected static final String REPLACE_NOTIFICATIONS_TOKEN = "#jenkinsNotificationToken";

    protected static final String REPLACE_STATIC_CODE_ANALYSIS_SCRIPT = "#staticCodeAnalysisScript";

    protected static final String REPLACE_DOCKER_IMAGE_NAME = "#dockerImage";

    protected static final String REPLACE_JENKINS_TIMEOUT = "#jenkinsTimeout";

    protected String artemisNotificationUrl;

    @Value("${artemis.continuous-integration.secret-push-token}")
    protected String pushToken;

    @Value("${artemis.continuous-integration.vcs-credentials}")
    protected String gitCredentialsKey;

    @Value("${artemis.continuous-integration.build-timeout:#{30}}")
    protected String buildTimeout;

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    protected String ARTEMIS_AUTHENTICATION_TOKEN_KEY;

    protected final ResourceLoaderService resourceLoaderService;

    protected final JenkinsService jenkinsService;

    public BaseJenkinsBuildPlanCreator(ResourceLoaderService resourceLoaderService, JenkinsService jenkinsService) {
        this.resourceLoaderService = resourceLoaderService;
        this.jenkinsService = jenkinsService;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_SERVER_URL + "/api" + Constants.NEW_RESULT_RESOURCE_PATH;
    }

    public String getPipelineScript(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        return replacePipelineScriptParameters(getResourcePath(programmingLanguage, testRepositoryURL, assignmentRepositoryURL, isStaticCodeAnalysisEnabled),
                getReplacements(programmingLanguage, testRepositoryURL, assignmentRepositoryURL, isStaticCodeAnalysisEnabled));
    }

    protected Map<String, String> getReplacements(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL,
            boolean isStaticCodeAnalysisEnabled) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(REPLACE_TEST_REPO, testRepositoryURL.toString());
        replacements.put(REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString());
        replacements.put(REPLACE_GIT_CREDENTIALS, gitCredentialsKey);
        replacements.put(REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH);
        replacements.put(REPLACE_TESTS_CHECKOUT_PATH, Constants.TESTS_CHECKOUT_PATH);
        replacements.put(REPLACE_ARTEMIS_NOTIFICATION_URL, artemisNotificationUrl);
        replacements.put(REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY);
        replacements.put(REPLACE_DOCKER_IMAGE_NAME, jenkinsService.getDockerImageName(programmingLanguage));
        replacements.put(REPLACE_JENKINS_TIMEOUT, buildTimeout);
        return replacements;
    }

    protected String[] getResourcePath(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        return new String[] { "templates", "jenkins", programmingLanguage.name().toLowerCase(), "Jenkinsfile" };
    }

    @Override
    public Document buildBasicConfig(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        final var resourcePath = Path.of("templates", "jenkins", "config.xml");

        String pipeLineScript = getPipelineScript(programmingLanguage, testRepositoryURL, assignmentRepositoryURL, isStaticCodeAnalysisEnabled);
        pipeLineScript = pipeLineScript.replace("'", "&apos;");
        pipeLineScript = pipeLineScript.replace("<", "&lt;");
        pipeLineScript = pipeLineScript.replace(">", "&gt;");
        pipeLineScript = pipeLineScript.replace("\\", "\\\\");

        Map<String, String> replacements = Map.of(REPLACE_PIPELINE_SCRIPT, pipeLineScript, REPLACE_PUSH_TOKEN, pushToken);

        final var xmlResource = resourceLoaderService.getResource(resourcePath.toString());
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    protected String replacePipelineScriptParameters(String[] pipelineScriptPath, Map<String, String> variablesToReplace) {
        final var resource = resourceLoaderService.getResource(pipelineScriptPath);
        try {
            var pipelineScript = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
            if (variablesToReplace != null) {
                for (final var replacement : variablesToReplace.entrySet()) {
                    pipelineScript = pipelineScript.replace(replacement.getKey(), replacement.getValue());
                }
            }

            return pipelineScript;
        }
        catch (IOException e) {
            final var errorMessage = "Error loading template Jenkins build XML: " + e.getMessage();
            LOG.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }
}
