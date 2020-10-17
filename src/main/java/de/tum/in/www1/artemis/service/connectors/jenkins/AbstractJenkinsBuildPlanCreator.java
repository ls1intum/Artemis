package de.tum.in.www1.artemis.service.connectors.jenkins;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

import de.tum.in.www1.artemis.config.Constants;

@Profile("jenkins")
public abstract class AbstractJenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractJenkinsBuildPlanCreator.class);

    protected static final String REPLACE_TEST_REPO = "#testRepository";

    protected static final String REPLACE_ASSIGNMENT_REPO = "#assignmentRepository";

    protected static final String REPLACE_GIT_CREDENTIALS = "#gitCredentials";

    protected static final String REPLACE_ASSIGNMENT_CHECKOUT_PATH = "#assignmentCheckoutPath";

    protected static final String REPLACE_TESTS_CHECKOUT_PATH = "#testsCheckoutPath";

    protected static final String REPLACE_PUSH_TOKEN = "#secretPushToken";

    protected static final String REPLACE_ARTEMIS_NOTIFICATION_URL = "#notificationsUrl";

    protected static final String REPLACE_NOTIFICATIONS_TOKEN = "#jenkinsNotificationToken";

    protected static final String REPLACE_STATIC_CODE_ANALYSIS_SCRIPT = "#staticCodeAnalysisScript";

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

    public AbstractJenkinsBuildPlanCreator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_SERVER_URL + "/api" + Constants.NEW_RESULT_RESOURCE_PATH;
    }
}
