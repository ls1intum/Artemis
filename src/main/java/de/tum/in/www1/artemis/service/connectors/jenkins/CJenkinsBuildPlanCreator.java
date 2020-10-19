package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Component
public class CJenkinsBuildPlanCreator extends AbstractJenkinsBuildPlanCreator {

    public CJenkinsBuildPlanCreator(ResourceLoader resourceLoader) {
        super(resourceLoader);
    }

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        final var resourcePath = Path.of("templates", "jenkins", "c", "config.xml");
        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_TESTS_CHECKOUT_PATH, Constants.TESTS_CHECKOUT_PATH,
                REPLACE_PUSH_TOKEN, pushToken, REPLACE_ARTEMIS_NOTIFICATION_URL, artemisNotificationUrl, REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY);

        final var xmlResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:" + resourcePath);
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }
}
