package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Component
public class JenkinsBuildPlanCreator implements JenkinsXmlConfigBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JenkinsBuildPlanCreator.class);

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

    private static final String REPLACE_DOCKER_IMAGE_NAME = "#dockerImage";

    private static final String REPLACE_JENKINS_TIMEOUT = "#jenkinsTimeout";

    private static final String REPLACE_DEFAULT_BRANCH = "#defaultBranch";

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

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final ResourceLoaderService resourceLoaderService;

    public JenkinsBuildPlanCreator(ResourceLoaderService resourceLoaderService, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.resourceLoaderService = resourceLoaderService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @PostConstruct
    public void init() {
        this.artemisNotificationUrl = ARTEMIS_SERVER_URL + Constants.NEW_RESULT_RESOURCE_API_PATH;
    }

   /*
   public String getPipelineScript(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs,
            boolean isStaticCodeAnalysisEnabled, boolean isSequentialRuns, boolean isTestwiseCoverageEnabled) {
        var pipelinePath = getResourcePath(programmingLanguage, projectType, isStaticCodeAnalysisEnabled, isSequentialRuns);
        var replacements = getReplacements(programmingLanguage, projectType, internalVcsRepositoryURLs);
        return replacePipelineScriptParameters(pipelinePath, replacements);
    }
    */

    private Map<String, String> getReplacements(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs) {
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
        replacements.put(REPLACE_DOCKER_IMAGE_NAME, programmingLanguageConfiguration.getImage(programmingLanguage, projectType));
        replacements.put(REPLACE_JENKINS_TIMEOUT, buildTimeout);
        replacements.put(REPLACE_DEFAULT_BRANCH, defaultBranch);

        /*
        // at the moment, only Java and Swift are supported
        if (isStaticCodeAnalysisEnabled) {
            String staticCodeAnalysisScript = createStaticCodeAnalysisScript(programmingLanguage, projectType);
            replacements.put(REPLACE_STATIC_CODE_ANALYSIS_SCRIPT, staticCodeAnalysisScript);
        }
        // replace testwise coverage analysis placeholder
        String testwiseCoverageAnalysisMavenProfile = "";
        String testwiseCoverageAnalysisGradleTask = "";
        String testwiseCoverageAnalysisScript = "";
        if (isTestwiseCoverageAnalysisEnabled) {
            testwiseCoverageAnalysisMavenProfile = "-Pcoverage";
            testwiseCoverageAnalysisGradleTask = "tiaTests --run-all-tests";
            testwiseCoverageAnalysisScript = createTestwiseCoverageAnalysisScript(projectType);
        }
        replacements.put(REPLACE_TESTWISE_COVERAGE_MAVEN_PROFILE, testwiseCoverageAnalysisMavenProfile);
        replacements.put(REPLACE_TESTWISE_COVERAGE_GRADLE_TASK, testwiseCoverageAnalysisGradleTask);
        replacements.put(REPLACE_TESTWISE_COVERAGE_SCRIPT, testwiseCoverageAnalysisScript);
        */

        return replacements;
    }
    /*
    private String[] getResourcePath(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, boolean isStaticCodeAnalysisEnabled, boolean isSequentialRuns) {
        if (programmingLanguage == null) {
            throw new IllegalArgumentException("ProgrammingLanguage should not be null");
        }
        final var pipelineScriptFilename = isStaticCodeAnalysisEnabled ? "Jenkinsfile-staticCodeAnalysis" : "Jenkinsfile";
        final var regularOrSequentialDir = isSequentialRuns ? "sequentialRuns" : "regularRuns";
        final var programmingLanguageName = programmingLanguage.name().toLowerCase();

        Optional<String> projectTypeName;

        // Set a project type name in case the chosen Jenkinsfile also depend on the project type
        if (projectType.isPresent() && ProgrammingLanguage.C.equals(programmingLanguage)) {
            projectTypeName = Optional.of(projectType.get().name().toLowerCase(Locale.ROOT));
        }
        else if (projectType.isPresent() && projectType.get().isGradle()) {
            projectTypeName = Optional.of("gradle");
        }
        // Maven is also the project type for all other Java exercises (also if the project type is not present)
        else if (ProgrammingLanguage.JAVA.equals(programmingLanguage)) {
            projectTypeName = Optional.of("maven");
        }
        else {
            projectTypeName = Optional.empty();
        }

        return projectTypeName.map(name -> new String[] { "templates", "jenkins", programmingLanguageName, name, regularOrSequentialDir, pipelineScriptFilename })
                .orElseGet(() -> new String[] { "templates", "jenkins", programmingLanguageName, regularOrSequentialDir, pipelineScriptFilename });
    }
    */

    @Override
    public Document buildBasicConfig(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs,
            boolean isStaticCodeAnalysisEnabled, boolean isSequentialRuns, boolean isTestwiseCoverageEnabled) {
        final var resourcePath = Path.of("templates", "jenkins", "config.xml");

        String jenkinsFileScript = Path.of("templates", "jenkins", "Jenkinsfile").toString();
        var replacements = getReplacements(programmingLanguage, projectType, internalVcsRepositoryURLs);
        replacePipelineScriptParameters(jenkinsFileScript, replacements);
            // getPipelineScript(programmingLanguage, projectType, internalVcsRepositoryURLs, isStaticCodeAnalysisEnabled, isSequentialRuns, isTestwiseCoverageEnabled);
        jenkinsFileScript = jenkinsFileScript.replace("'", "&apos;");
        jenkinsFileScript = jenkinsFileScript.replace("<", "&lt;");
        jenkinsFileScript = jenkinsFileScript.replace(">", "&gt;");
        jenkinsFileScript = jenkinsFileScript.replace("\\", "\\\\");

        replacements = Map.of(REPLACE_PIPELINE_SCRIPT, jenkinsFileScript, REPLACE_PUSH_TOKEN, pushToken);

        final var xmlResource = resourceLoaderService.getResource(resourcePath.toString());
        return XmlFileUtils.readXmlFile(xmlResource, replacements);
    }

    private void replacePipelineScriptParameters(String jenkinsFileScript, Map<String, String> variablesToReplace) {
        if (variablesToReplace != null) {
            for (final var replacement : variablesToReplace.entrySet()) {
                jenkinsFileScript = jenkinsFileScript.replace(replacement.getKey(), replacement.getValue());
            }
        }
    }
}
