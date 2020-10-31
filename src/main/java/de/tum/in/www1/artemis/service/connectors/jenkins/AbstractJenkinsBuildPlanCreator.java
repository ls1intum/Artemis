package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
public abstract class AbstractJenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractJenkinsBuildPlanCreator.class);

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

    protected String artemisNotificationUrl;

    @Value("${artemis.continuous-integration.secret-push-token}")
    protected String pushToken;

    @Value("${artemis.continuous-integration.vcs-credentials}")
    protected String gitCredentialsKey;

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    protected String ARTEMIS_AUTHENTICATION_TOKEN_KEY;

    protected final ResourceLoader resourceLoader;

    protected final JenkinsService jenkinsService;

    public AbstractJenkinsBuildPlanCreator(ResourceLoader resourceLoader, JenkinsService jenkinsService) {
        this.resourceLoader = resourceLoader;
        this.jenkinsService = jenkinsService;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_SERVER_URL + "/api" + Constants.NEW_RESULT_RESOURCE_PATH;
    }

    public abstract String getPipelineScript(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled);

    @Override
    public Document buildBasicConfig(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        final var resourcePath = Path.of("templates", "jenkins", "config.xml");

        String pipeLineScript = getPipelineScript(programmingLanguage, testRepositoryURL, assignmentRepositoryURL, isStaticCodeAnalysisEnabled);
        pipeLineScript = pipeLineScript.replace("'", "&apos;");
        pipeLineScript = pipeLineScript.replace("<", "&lt;");
        pipeLineScript = pipeLineScript.replace(">", "&gt;");
        pipeLineScript = pipeLineScript.replace("\\", "\\\\");

        Map<String, String> replacements = Map.of(REPLACE_PIPELINE_SCRIPT, pipeLineScript, REPLACE_PUSH_TOKEN, pushToken);

        final var xmlResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:" + resourcePath);
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    protected String replacePipelineScriptParameters(Path pipelineScriptPath, Map<String, String> variablesToReplace) {
        final var resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:" + pipelineScriptPath);
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
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }
}
