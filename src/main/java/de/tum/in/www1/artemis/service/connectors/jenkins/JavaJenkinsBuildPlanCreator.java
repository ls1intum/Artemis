package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Component
public class JavaJenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(JavaJenkinsBuildPlanCreator.class);

    private static final String REPLACE_TEST_REPO = "#testRepository";

    private static final String REPLACE_ASSIGNMENT_REPO = "#assignmentRepository";

    private static final String REPLACE_GIT_CREDENTIALS = "#gitCredentials";

    private static final String REPLACE_ASSIGNMENT_CHECKOUT_PATH = "#assignmentCheckoutPath";

    private static final String REPLACE_PUSH_TOKEN = "#secretPushToken";

    private static final String REPLACE_ARTEMIS_NOTIFICATION_URL = "#notificationsUrl";

    private static final String REPLACE_NOTIFICATIONS_TOKEN = "#jenkinsNotificationToken";

    private String artemisNotificationUrl;

    @Value("${artemis.continuous-integration.secret-push-token}")
    private String pushToken;

    @Value("${artemis.continuous-integration.vcs-credentials}")
    private String gitCredentialsKey;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_KEY;

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_BASE_URL + "/api" + Constants.NEW_RESULT_RESOURCE_PATH;
    }

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL) {
        final var resourcePath = Path.of("templates", "jenkins", "java", "config.xml");
        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_PUSH_TOKEN, pushToken, REPLACE_ARTEMIS_NOTIFICATION_URL,
                artemisNotificationUrl, REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY);

        return XmlFileUtils.readXmlFile(resourcePath, replacements);
    }

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL, boolean isSequential) {
        if (!isSequential) {
            return buildBasicConfig(testRepositoryURL, assignmentRepositoryURL);
        }

        throw new UnsupportedOperationException("Sequential Jenkins builds not yet supported for Java!");
    }
}
