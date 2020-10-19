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
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Component
public class JavaJenkinsBuildPlanCreator extends AbstractJenkinsBuildPlanCreator {

    private static final String STATIC_CODE_ANALYSIS_REPORT_DIR = "staticCodeAnalysisReports";

    public JavaJenkinsBuildPlanCreator(ResourceLoader resourceLoader) {
        super(resourceLoader);
    }

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL) {
        final var resourcePath = Path.of("templates", "jenkins", "java", "config.xml");
        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_PUSH_TOKEN, pushToken, REPLACE_ARTEMIS_NOTIFICATION_URL,
                artemisNotificationUrl, REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY);

        final var xmlResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:" + resourcePath);
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    @Override
    public Document buildConfigWithStaticCodeAnalysis(URL testRepositoryURL, URL assignmentRepositoryURL) {
        final var resourcePath = Path.of("templates", "jenkins", "java", "configWithStaticCodeAnalysis.xml");
        final var staticCodeAnalysisScript = createStaticCodeAnalysisScript();
        final var replacements = Map.of(REPLACE_TEST_REPO, testRepositoryURL.toString(), REPLACE_ASSIGNMENT_REPO, assignmentRepositoryURL.toString(), REPLACE_GIT_CREDENTIALS,
                gitCredentialsKey, REPLACE_ASSIGNMENT_CHECKOUT_PATH, Constants.ASSIGNMENT_CHECKOUT_PATH, REPLACE_PUSH_TOKEN, pushToken, REPLACE_ARTEMIS_NOTIFICATION_URL,
                artemisNotificationUrl, REPLACE_NOTIFICATIONS_TOKEN, ARTEMIS_AUTHENTICATION_TOKEN_KEY, REPLACE_STATIC_CODE_ANALYSIS_SCRIPT, staticCodeAnalysisScript);

        final var xmlResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:" + resourcePath);
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    @Override
    public Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL, boolean isSequential) {
        if (!isSequential) {
            return buildBasicConfig(testRepositoryURL, assignmentRepositoryURL);
        }

        throw new UnsupportedOperationException("Sequential Jenkins builds not yet supported for Java!");
    }

    private String createStaticCodeAnalysisScript() {
        StringBuilder script = new StringBuilder();
        String lineEnding = "&#xd;";
        script.append("mvn ");
        // Execute all static code analysis tools for Java
        script.append(StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage.JAVA)).append(lineEnding);
        // Make directory for generated static code analysis reports
        script.append("mkdir ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        // Copy all static code analysis reports to new directory
        for (var tool : StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.JAVA)) {
            script.append("cp target/").append(tool.getFilePattern()).append(" ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        }
        return script.toString();
    }
}
