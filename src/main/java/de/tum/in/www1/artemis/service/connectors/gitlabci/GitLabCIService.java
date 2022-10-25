package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.net.URL;
import java.util.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestCaseDTO;
import org.apache.commons.collections.CollectionUtils;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.PipelineStatus;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Trigger;
import org.gitlab4j.api.models.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.GitLabCIException;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Profile("gitlabci")
@Service
public class GitLabCIService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIService.class);

    private static final String VARIABLE_BUILD_DOCKER_IMAGE_NAME = "ARTEMIS_BUILD_DOCKER_IMAGE";

    private static final String VARIABLE_BUILD_LOGS_FILE_NAME = "ARTEMIS_BUILD_LOGS_FILE";

    private static final String VARIABLE_BUILD_PLAN_ID_NAME = "ARTEMIS_BUILD_PLAN_ID";

    private static final String VARIABLE_CUSTOM_FEEDBACK_DIR_NAME = "ARTEMIS_CUSTOM_FEEDBACK_DIR";

    private static final String VARIABLE_NOTIFICATION_PLUGIN_DOCKER_IMAGE_NAME = "ARTEMIS_NOTIFICATION_PLUGIN_DOCKER_IMAGE";

    private static final String VARIABLE_NOTIFICATION_SECRET_NAME = "ARTEMIS_NOTIFICATION_SECRET";

    private static final String VARIABLE_NOTIFICATION_URL_NAME = "ARTEMIS_NOTIFICATION_URL";

    private static final String VARIABLE_SUBMISSION_GIT_BRANCH_NAME = "ARTEMIS_SUBMISSION_GIT_BRANCH";

    private static final String VARIABLE_TEST_GIT_BRANCH_NAME = "ARTEMIS_TEST_GIT_BRANCH";

    private static final String VARIABLE_TEST_GIT_REPOSITORY_SLUG_NAME = "ARTEMIS_TEST_GIT_REPOSITORY_SLUG";

    private static final String VARIABLE_TEST_RESULTS_DIR_NAME = "ARTEMIS_TEST_RESULTS_DIR";

    private final GitLabApi gitlab;

    private final UrlService urlService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final BuildPlanRepository buildPlanRepository;

    private final GitLabCIBuildPlanService buildPlanService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final TestwiseCoverageService testwiseCoverageService;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Value("${artemis.continuous-integration.notification-plugin}")
    private String notificationPluginDockerImage;

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String artemisAuthenticationTokenValue;

    public GitLabCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UrlService urlService, ProgrammingExerciseRepository programmingExerciseRepository,
            BuildPlanRepository buildPlanRepository, GitLabCIBuildPlanService buildPlanService, ProgrammingLanguageConfiguration programmingLanguageConfiguration,
            TestwiseCoverageService testwiseCoverageService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, restTemplate, shortTimeoutRestTemplate);
        this.gitlab = gitlab;
        this.urlService = urlService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.buildPlanRepository = buildPlanRepository;
        this.buildPlanService = buildPlanService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.testwiseCoverageService = testwiseCoverageService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        addBuildPlanToProgrammingExerciseIfUnset(exercise);
        setupGitLabCIConfiguration(repositoryURL, exercise, planKey);
        // TODO: triggerBuild(repositoryURL, exercise.getBranch());
    }

    private void setupGitLabCIConfiguration(VcsRepositoryUrl repositoryURL, ProgrammingExercise exercise, String buildPlanId) {
        final String repositoryPath = getRepositoryPath(repositoryURL);
        ProjectApi projectApi = gitlab.getProjectApi();
        try {
            Project project = projectApi.getProject(repositoryPath);

            project.setJobsEnabled(true);
            project.setSharedRunnersEnabled(true);
            project.setAutoDevopsEnabled(false);

            project.setCiConfigPath(generateBuildPlanURL(exercise));

            projectApi.updateProject(project);
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error enabling CI for " + repositoryURL.toString(), e);
        }

        try {
            // TODO: Reduce the number of API calls

            updateVariable(repositoryPath, VARIABLE_BUILD_DOCKER_IMAGE_NAME, programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType())), true);
            updateVariable(repositoryPath, VARIABLE_BUILD_LOGS_FILE_NAME, "build.log", true);
            updateVariable(repositoryPath, VARIABLE_BUILD_PLAN_ID_NAME, buildPlanId, true);
            updateVariable(repositoryPath, VARIABLE_CUSTOM_FEEDBACK_DIR_NAME, "TODO", false); // TODO
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_PLUGIN_DOCKER_IMAGE_NAME, notificationPluginDockerImage, true);
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_SECRET_NAME, artemisAuthenticationTokenValue, true);
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_URL_NAME, artemisServerUrl.toExternalForm() + Constants.NEW_RESULT_RESOURCE_API_PATH, false);
            updateVariable(repositoryPath, VARIABLE_SUBMISSION_GIT_BRANCH_NAME, exercise.getBranch(), false);
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_BRANCH_NAME, exercise.getBranch(), false);
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_REPOSITORY_SLUG_NAME, urlService.getRepositorySlugFromRepositoryUrlString(exercise.getTestRepositoryUrl()), true);
            updateVariable(repositoryPath, VARIABLE_TEST_RESULTS_DIR_NAME, "target/surefire-reports", true);
        }
        catch (GitLabApiException e) {
            log.error("Error creating variable for " + repositoryURL.toString() + " The variables may already have been created.", e);
        }
    }

    private void updateVariable(String repositoryPath, String key, String value, boolean masked) throws GitLabApiException {
        gitlab.getProjectApi().createVariable(repositoryPath, key, value, Variable.Type.ENV_VAR, false, masked);
    }

    private void addBuildPlanToProgrammingExerciseIfUnset(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildPlan() == null) {
            programmingExerciseRepository.updateBuildPlan(programmingExercise, buildPlanService.getBuildPlan(programmingExercise), buildPlanRepository);
        }
    }

    private String getRepositoryPath(VcsRepositoryUrl repositoryUrl) {
        return urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        addBuildPlanToProgrammingExerciseIfUnset(exercise);

        VcsRepositoryUrl templateUrl = exercise.getVcsTemplateRepositoryUrl();
        setupGitLabCIConfiguration(templateUrl, exercise, exercise.getTemplateBuildPlanId());
        // TODO: triggerBuild(templateUrl, exercise.getBranch());

        VcsRepositoryUrl solutionUrl = exercise.getVcsSolutionRepositoryUrl();
        setupGitLabCIConfiguration(solutionUrl, exercise, exercise.getSolutionBuildPlanId());
        // TODO: triggerBuild(solutionUrl, exercise.getBranch());
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        // In GitLab CI we don't have to copy the build plan.
        // Instead, we configure a CI config path leading to the API when enabling the CI.
        log.error("Unsupported action: GitLabCIService.copyBuildPlan()");

        // When sending the build results back, the build plan key is used to identify the participation.
        // Therefore, we return the key here even though GitLab CI does not need it.
        return targetProjectKey + "-" + targetPlanName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        setupGitLabCIConfiguration(participation.getVcsRepositoryUrl(), participation.getProgrammingExercise(), participation.getBuildPlanId());
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        log.error("Unsupported action: GitLabCIService.performEmptySetupCommit()");
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        triggerBuild(participation.getVcsRepositoryUrl(), participation.getProgrammingExercise().getBranch());
    }

    private void triggerBuild(VcsRepositoryUrl vcsRepositoryUrl, String branch) {
        final String repositoryPath = getRepositoryPath(vcsRepositoryUrl);
        try {
            Trigger trigger = gitlab.getPipelineApi().createPipelineTrigger(repositoryPath, "Trigger build");
            gitlab.getPipelineApi().triggerPipeline(repositoryPath, trigger, branch, null);
            gitlab.getPipelineApi().deletePipelineTrigger(repositoryPath, trigger.getId());
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error triggering the build for " + repositoryPath, e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        log.error("Unsupported action: GitLabCIService.deleteBuildPlan()");
        log.error("Please refer to the repository for deleting the project. The build plan can not be deleted separately.");
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        log.error("Unsupported action: GitLabCIService.deleteBuildPlan()");
        log.error("Please refer to the repository for deleting the project. The build plan can not be deleted separately.");
    }

    @Override
    public String getPlanKey(Object requestBody) throws ContinuousIntegrationException {
        TestResultsDTO dto = TestResultsDTO.convert(requestBody);
        return dto.getFullName();
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        return TestResultsDTO.convert(requestBody);
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // https://docs.gitlab.com/ee/api/pipelines.html#list-project-pipelines
        final String repositoryPath = getRepositoryPath(participation.getVcsRepositoryUrl());
        try {
            // TODO: Get latest pipeline
            PipelineStatus status = gitlab.getPipelineApi().getPipelines(repositoryPath).get(0).getStatus();
            if (status.equals(PipelineStatus.CREATED) || status.equals(PipelineStatus.WAITING_FOR_RESOURCE) || status.equals(PipelineStatus.PREPARING)
                    || status.equals(PipelineStatus.PENDING)) {
                return BuildStatus.QUEUED;
            }
            else if (status.equals(PipelineStatus.RUNNING)) {
                return BuildStatus.BUILDING;
            }
            else {
                return BuildStatus.INACTIVE;
            }
        }
        catch (GitLabApiException | IndexOutOfBoundsException e) {
            return BuildStatus.INACTIVE;
        }
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        log.error("Unsupported action: GitLabCIService.checkIfBuildPlanExists()");
        return false;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        log.error("Unsupported action: GitLabCIService.getLatestBuildLogs()");
        return null;
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        log.error("Unsupported action: GitLabCIService.retrieveLatestArtifact()");
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        log.error("Unsupported action: GitLabCIService.checkIfProjectExists()");
        return null;
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
        log.error("Unsupported action: GitLabCIService.enablePlan()");
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newDefaultBranch, Optional<List<String>> optionalTriggeredByRepositories) {
        log.error("Unsupported action: GitLabCIService.updatePlanRepository()");
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        log.error("Unsupported action: GitLabCIService.giveProjectPermissions()");
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        log.error("Unsupported action: GitLabCIService.givePlanPermissions()");
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        log.error("Unsupported action: GitLabCIService.removeAllDefaultProjectPermissions()");
    }

    @Override
    public ConnectorHealth health() {
        return new ConnectorHealth(true, Map.of("cf.", "Version Control Server", "url", gitlabServerUrl));
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        log.error("Unsupported action: GitLabCIService.createProjectForExercise()");
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        log.error("Unsupported action: GitLabCIService.getWebHookUrl()");
        // TODO
        return Optional.empty();
    }

    private String generateBuildPlanURL(ProgrammingExercise exercise) {
        programmingExerciseRepository.generateBuildPlanAccessSecretIfNotExists(exercise);
        // We need this workaround (&file-extension=.yml) since GitLab only accepts URLs ending with .yml.
        // See https://gitlab.com/gitlab-org/gitlab/-/issues/27526
        return String.format("%s/api/programming-exercises/%s/build-plan?secret=%s&file-extension=.yml", artemisServerUrl, exercise.getId(), exercise.getBuildPlanAccessSecret());
    }

    @Override
    protected void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {
        final var testResults = ((TestResultsDTO) buildResult);
        final var jobs = testResults.getResults();
        final var programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        final var programmingLanguage = programmingExercise.getProgrammingLanguage();
        final var projectType = programmingExercise.getProjectType();

        // Extract test case feedback
        for (final var job : jobs) {
            for (final var testCase : job.getTestCases()) {
                var feedbackMessages = extractMessageFromTestCase(testCase).map(List::of).orElse(List.of());
                var feedback = feedbackRepository.createFeedbackFromTestCase(testCase.getName(), feedbackMessages, testCase.isSuccessful(), programmingLanguage, projectType);
                result.addFeedback(feedback);
            }

            int passedTestCasesAmount = (int) job.getTestCases().stream().filter(TestCaseDTO::isSuccessful).count();
            result.setTestCaseCount(result.getTestCaseCount() + job.getTests());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + passedTestCasesAmount);
        }

        // Extract static code analysis feedback if option was enabled
        final var staticCodeAnalysisReports = testResults.getStaticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            var scaFeedbackList = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }

        final var testwiseCoverageReport = testResults.getTestwiseCoverageReport();
        if (Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()) && testwiseCoverageReport != null && !testwiseCoverageReport.isEmpty()) {
            // since the test cases are not saved to the database yet, the test case is null for the entries
            var coverageFileReportsWithoutTestsByTestCaseName = testwiseCoverageService.createTestwiseCoverageFileReportsWithoutTestsByTestCaseName(testwiseCoverageReport);
            result.setCoverageFileReportsByTestCaseName(coverageFileReportsWithoutTestsByTestCaseName);
        }

        // Relevant feedback is negative, or positive with a message
        result.setHasFeedback(result.getFeedbacks().stream().anyMatch(feedback -> !feedback.isPositive() || feedback.getDetailText() != null));
    }

    /**
     * Extracts the most helpful message from the given test case.
     * @param testCase the test case information as received from Jenkins.
     * @return the most helpful message that can be added to an automatic {@link Feedback}.
     */
    private Optional<String> extractMessageFromTestCase(final TestCaseDTO testCase) {
        var hasErrors = !CollectionUtils.isEmpty(testCase.getErrors());
        var hasFailures = !CollectionUtils.isEmpty(testCase.getFailures());
        var hasSuccessInfos = !CollectionUtils.isEmpty(testCase.getSuccessInfos());
        boolean successful = testCase.isSuccessful();

        if (successful && hasSuccessInfos && testCase.getSuccessInfos().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getSuccessInfos().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && testCase.getErrors().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getErrors().get(0).getMostInformativeMessage());
        }
        else if (hasFailures && testCase.getFailures().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getFailures().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && testCase.getErrors().get(0).getType() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", testCase.getErrors().get(0).getType()));
        }
        else if (hasFailures && testCase.getFailures().get(0).getType() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", testCase.getFailures().get(0).getType()));
        }
        else if (!successful) {
            // this is an edge case which typically does not happen
            return Optional.of("Unsuccessful due to an unknown error. Please contact your instructor!");
        }
        else {
            // successful and no message available => do not generate one
            return Optional.empty();
        }
    }
}
