package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

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

    @Value("${artemis.jenkins.secret-push-token}")
    private String pushToken;

    @Value("${artemis.jenkins.git-credentials}")
    private String gitCredentialsKey;

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL) {
        final var resourcePath = Path.of("build", "jenkins", "java", "config.xml");
        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_PUSH_TOKEN, pushToken);

        return XmlFileUtils.readXmlFile(resourcePath, replacements);
    }
}
