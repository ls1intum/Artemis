package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.config.Constants;

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
        final var configXmlFile = new File(getClass().getClassLoader().getResource(resourcePath.toString()).getFile());
        try {
            var configXmlText = FileUtils.readFileToString(configXmlFile, CharEncoding.UTF_8);
            configXmlText = configXmlText.replace(REPLACE_TEST_REPO, testRepositoryURL.toString());
            configXmlText = configXmlText.replace(REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString());
            configXmlText = configXmlText.replace(REPLACE_GIT_CREDENTIALS, gitCredentialsKey);
            configXmlText = configXmlText.replace(REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH);
            configXmlText = configXmlText.replace(REPLACE_PUSH_TOKEN, pushToken);

            final var domFactory = DocumentBuilderFactory.newInstance();
            final var builder = domFactory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(configXmlText)));
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            final var errorMessage = "Error loading template Jenins build XML: " + e.getMessage();
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }
}
