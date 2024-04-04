package de.tum.in.www1.artemis.service.connectors.gitlabci;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.GroupApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.GitLabCIException;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ci.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Profile("gitlabci")
@Service
public class GitLabCIService extends AbstractContinuousIntegrationService {

    private static final String GITLAB_CI_FILE_EXTENSION = ".yml";

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

    private static final String VARIABLE_TEST_GIT_TOKEN = "ARTEMIS_TEST_GIT_TOKEN";

    private static final String VARIABLE_TEST_GIT_USER = "ARTEMIS_TEST_GIT_USER";

    private static final String VARIABLE_TEST_RESULTS_DIR_NAME = "ARTEMIS_TEST_RESULTS_DIR";

    private final GitLabApi gitlab;

    private final UriService uriService;

    private final BuildPlanRepository buildPlanRepository;

    private final GitLabCIBuildPlanService buildPlanService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Value("${artemis.continuous-integration.notification-plugin}")
    private String notificationPluginDockerImage;

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String artemisAuthenticationTokenValue;

    @Value("${artemis.version-control.user}")
    private String gitlabUser;

    @Value("${artemis.version-control.token}")
    private String gitlabToken;

    public GitLabCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            GitLabApi gitlab, UriService uriService, BuildPlanRepository buildPlanRepository, GitLabCIBuildPlanService buildPlanService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository,
            TestwiseCoverageService testwiseCoverageService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService);
        this.gitlab = gitlab;
        this.uriService = uriService;
        this.buildPlanRepository = buildPlanRepository;
        this.buildPlanService = buildPlanService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {
        addBuildPlanToProgrammingExercise(exercise, false);
        // This method is called twice when creating an exercise. Once for the template repository and once for the solution repository.
        // The second time, we don't want to overwrite the configuration.
        setupGitLabCIConfigurationForGroup(exercise, false);
        setupGitLabCIConfigurationForRepository(repositoryUri, exercise, planKey);
    }

    private void setupGitLabCIConfigurationForRepository(VcsRepositoryUri repositoryUri, ProgrammingExercise exercise, String buildPlanId) {
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        final ProjectApi projectApi = gitlab.getProjectApi();
        try {
            Project project = projectApi.getProject(repositoryPath);

            project.setJobsEnabled(true);
            project.setSharedRunnersEnabled(true);
            project.setAutoDevopsEnabled(false);

            final String buildPlanUrl = buildPlanService.generateBuildPlanURL(exercise) + "&file-extension=" + GITLAB_CI_FILE_EXTENSION;
            project.setCiConfigPath(buildPlanUrl);

            projectApi.updateProject(project);

            setRepositoryVariable(repositoryPath, VARIABLE_BUILD_PLAN_ID_NAME, buildPlanId);
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error enabling CI for " + repositoryUri, e);
        }
    }

    private void setupGitLabCIConfigurationForGroup(ProgrammingExercise exercise, boolean overwrite) {
        final String projectKey = exercise.getProjectKey();
        updateGroupVariable(projectKey, VARIABLE_BUILD_DOCKER_IMAGE_NAME,
                programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType())), overwrite);
        updateGroupVariable(projectKey, VARIABLE_BUILD_LOGS_FILE_NAME, "build.log", overwrite);
        // TODO: Implement the custom feedback feature
        updateGroupVariable(projectKey, VARIABLE_CUSTOM_FEEDBACK_DIR_NAME, "TODO", overwrite);
        updateGroupVariable(projectKey, VARIABLE_NOTIFICATION_PLUGIN_DOCKER_IMAGE_NAME, notificationPluginDockerImage, overwrite);
        updateGroupVariable(projectKey, VARIABLE_NOTIFICATION_SECRET_NAME, artemisAuthenticationTokenValue, overwrite);
        updateGroupVariable(projectKey, VARIABLE_NOTIFICATION_URL_NAME, artemisServerUrl.toExternalForm() + NEW_RESULT_RESOURCE_API_PATH, overwrite);
        updateGroupVariable(projectKey, VARIABLE_SUBMISSION_GIT_BRANCH_NAME, exercise.getBranch(), overwrite);
        updateGroupVariable(projectKey, VARIABLE_TEST_GIT_BRANCH_NAME, exercise.getBranch(), overwrite);
        updateGroupVariable(projectKey, VARIABLE_TEST_GIT_REPOSITORY_SLUG_NAME, uriService.getRepositorySlugFromRepositoryUriString(exercise.getTestRepositoryUri()), overwrite);
        // TODO: Use a token that is only valid for the test repository for each programming exercise
        updateGroupVariable(projectKey, VARIABLE_TEST_GIT_TOKEN, gitlabToken, overwrite);
        updateGroupVariable(projectKey, VARIABLE_TEST_GIT_USER, gitlabUser, overwrite);
        updateGroupVariable(projectKey, VARIABLE_TEST_RESULTS_DIR_NAME, "target/surefire-reports", overwrite);
    }

    private void updateGroupVariable(String projectKey, String key, String value, boolean overwrite) {
        final GroupApi groupApi = gitlab.getGroupApi();
        if (groupApi.getOptionalVariable(projectKey, key).isEmpty()) {
            try {
                groupApi.createVariable(projectKey, key, value, false, canBeMasked(value));
            }
            catch (GitLabApiException e) {
                log.error("Error creating variable '{}' for group {}", key, projectKey, e);
            }
        }
        else if (overwrite) {
            try {
                groupApi.updateVariable(projectKey, key, value, false, canBeMasked(value));
            }
            catch (GitLabApiException e) {
                log.error("Error updating variable '{}' for group {}", key, projectKey, e);
            }
        }
    }

    private void setRepositoryVariable(String repositoryPath, String key, String value) {
        final ProjectApi projectApi = gitlab.getProjectApi();
        if (projectApi.getOptionalVariable(repositoryPath, key).isEmpty()) {
            try {
                projectApi.createVariable(repositoryPath, key, value, Variable.Type.ENV_VAR, false, canBeMasked(value));
            }
            catch (GitLabApiException e) {
                log.error("Error creating variable '{}' for repository {}", key, repositoryPath, e);
            }
        }
    }

    private boolean canBeMasked(String value) {
        // This regex matches which can be masked, c.f. https://docs.gitlab.com/ee/ci/variables/#mask-a-cicd-variable
        return value != null && value.matches("^[a-zA-Z0-9+/=@:.~]{8,}$");
    }

    private void addBuildPlanToProgrammingExercise(ProgrammingExercise programmingExercise, boolean overwrite) {
        Optional<BuildPlan> optionalBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        if (optionalBuildPlan.isEmpty() || overwrite) {
            var defaultBuildPlan = buildPlanService.generateDefaultBuildPlan(programmingExercise);
            buildPlanRepository.setBuildPlanForExercise(defaultBuildPlan, programmingExercise);
        }
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        addBuildPlanToProgrammingExercise(exercise, true);
        // When recreating the build plan for the exercise, we want to overwrite the configuration.
        setupGitLabCIConfigurationForGroup(exercise, true);

        VcsRepositoryUri templateUri = exercise.getVcsTemplateRepositoryUri();
        setupGitLabCIConfigurationForRepository(templateUri, exercise, exercise.getTemplateBuildPlanId());

        VcsRepositoryUri solutionUri = exercise.getVcsSolutionRepositoryUri();
        setupGitLabCIConfigurationForRepository(solutionUri, exercise, exercise.getSolutionBuildPlanId());
    }

    @Override
    public String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        // In GitLab CI we don't have to copy the build plan.
        // Instead, we configure a CI config path leading to the API when enabling the CI.

        // When sending the build results back, the build plan key is used to identify the participation.
        // Therefore, we return the key here even though GitLab CI does not need it.
        return generateBuildPlanId(targetExercise.getProjectKey(), targetPlanName);
    }

    private String generateBuildPlanId(String projectKey, String planKey) {
        return projectKey + "-" + planKey.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        setupGitLabCIConfigurationForRepository(participation.getVcsRepositoryUri(), participation.getProgrammingExercise(), participation.getBuildPlanId());
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
        // TODO
    }

    @Override
    public String getPlanKey(Object requestBody) throws ContinuousIntegrationException {
        TestResultsDTO dto = TestResultsDTO.convert(requestBody);
        return dto.getFullName();
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        try {
            final Optional<Pipeline> optionalPipeline = getLatestPipeline(participation);
            if (optionalPipeline.isEmpty()) {
                return BuildStatus.INACTIVE;
            }
            return convertPipelineStatusToBuildStatus(optionalPipeline.get().getStatus());
        }
        catch (GitLabApiException e) {
            return BuildStatus.INACTIVE;
        }
    }

    private BuildStatus convertPipelineStatusToBuildStatus(PipelineStatus status) {
        // TODO
        return switch (status) {
            case CREATED, WAITING_FOR_RESOURCE, PREPARING, PENDING -> BuildStatus.QUEUED;
            case RUNNING -> BuildStatus.BUILDING;
            default -> BuildStatus.INACTIVE;
        };
    }

    private Optional<Pipeline> getLatestPipeline(final ProgrammingExerciseParticipation participation) throws GitLabApiException {
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(participation.getVcsRepositoryUri());
        final Optional<String> commitHash = participation.findLatestSubmission().map(ProgrammingSubmission.class::cast).map(ProgrammingSubmission::getCommitHash);
        if (commitHash.isEmpty()) {
            return Optional.empty();
        }

        return gitlab.getPipelineApi().getPipelinesStream(repositoryPath, new PipelineFilter().withSha(commitHash.get())).max(Comparator.comparing(Pipeline::getId));
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        log.error("Unsupported action: GitLabCIService.checkIfBuildPlanExists()");
        return true;
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
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri,
            String newDefaultBranch) {
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
        return Optional.empty();
    }
}
