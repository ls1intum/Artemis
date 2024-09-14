package de.tum.cit.aet.artemis.programming.service.gitlabci;

import static de.tum.cit.aet.artemis.core.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.Pipeline;
import org.gitlab4j.api.models.PipelineFilter;
import org.gitlab4j.api.models.PipelineStatus;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.GitLabCIException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.buildagent.domain.BuildPlan;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.CIPermission;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestResultsDTO;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

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

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

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

    public GitLabCIService(GitLabApi gitlab, UriService uriService, BuildPlanRepository buildPlanRepository, GitLabCIBuildPlanService buildPlanService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.gitlab = gitlab;
        this.uriService = uriService;
        this.buildPlanRepository = buildPlanRepository;
        this.buildPlanService = buildPlanService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {
        addBuildPlanToProgrammingExerciseIfUnset(exercise);
        setupGitLabCIConfiguration(repositoryUri, exercise, generateBuildPlanId(exercise.getProjectKey(), planKey));
        // TODO: triggerBuild(repositoryUri, exercise.getBranch());
    }

    private void setupGitLabCIConfiguration(VcsRepositoryUri repositoryUri, ProgrammingExercise exercise, String buildPlanId) {
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        ProjectApi projectApi = gitlab.getProjectApi();

        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(exercise);

        try {
            Project project = projectApi.getProject(repositoryPath);

            project.setJobsEnabled(true);
            project.setSharedRunnersEnabled(true);
            project.setAutoDevopsEnabled(false);

            final String buildPlanUrl = buildPlanService.generateBuildPlanURL(exercise) + "&file-extension=" + GITLAB_CI_FILE_EXTENSION;
            project.setCiConfigPath(buildPlanUrl);

            projectApi.updateProject(project);
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error enabling CI for " + repositoryUri, e);
        }

        try {
            // TODO: Reduce the number of API calls
            ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
            updateVariable(repositoryPath, VARIABLE_BUILD_DOCKER_IMAGE_NAME,
                    programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType())));
            updateVariable(repositoryPath, VARIABLE_BUILD_LOGS_FILE_NAME, "build.log");
            updateVariable(repositoryPath, VARIABLE_BUILD_PLAN_ID_NAME, buildPlanId);
            // TODO: Implement the custom feedback feature
            updateVariable(repositoryPath, VARIABLE_CUSTOM_FEEDBACK_DIR_NAME, "TODO");
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_PLUGIN_DOCKER_IMAGE_NAME, notificationPluginDockerImage);
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_SECRET_NAME, artemisAuthenticationTokenValue);
            updateVariable(repositoryPath, VARIABLE_NOTIFICATION_URL_NAME, artemisServerUrl.toExternalForm() + NEW_RESULT_RESOURCE_API_PATH);
            updateVariable(repositoryPath, VARIABLE_SUBMISSION_GIT_BRANCH_NAME, buildConfig.getBranch());
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_BRANCH_NAME, buildConfig.getBranch());
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_REPOSITORY_SLUG_NAME, uriService.getRepositorySlugFromRepositoryUriString(exercise.getTestRepositoryUri()));
            // TODO: Use a token that is only valid for the test repository for each programming exercise
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_TOKEN, gitlabToken);
            updateVariable(repositoryPath, VARIABLE_TEST_GIT_USER, gitlabUser);
            updateVariable(repositoryPath, VARIABLE_TEST_RESULTS_DIR_NAME, "target/surefire-reports");
        }
        catch (GitLabApiException e) {
            log.error("Error creating variable for {} The variables may already have been created.", repositoryUri, e);
        }
    }

    private void updateVariable(String repositoryPath, String key, String value) throws GitLabApiException {
        // TODO: We can even define the variables on group level
        // TODO: If the variable already exists, we should update it
        gitlab.getProjectApi().createVariable(repositoryPath, key, value, Variable.Type.ENV_VAR, false, canBeMasked(value));
    }

    private boolean canBeMasked(String value) {
        // This regex matches which can be masked, c.f. https://docs.gitlab.com/ee/ci/variables/#mask-a-cicd-variable
        return value != null && value.matches("^[a-zA-Z0-9+/=@:.~]{8,}$");
    }

    private void addBuildPlanToProgrammingExerciseIfUnset(ProgrammingExercise programmingExercise) {
        Optional<BuildPlan> optionalBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        if (optionalBuildPlan.isEmpty()) {
            var defaultBuildPlan = buildPlanService.generateDefaultBuildPlan(programmingExercise);
            buildPlanRepository.setBuildPlanForExercise(defaultBuildPlan, programmingExercise);
        }
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        addBuildPlanToProgrammingExerciseIfUnset(exercise);

        VcsRepositoryUri templateUrl = exercise.getVcsTemplateRepositoryUri();
        setupGitLabCIConfiguration(templateUrl, exercise, exercise.getTemplateBuildPlanId());
        // TODO: triggerBuild(templateUrl, exercise.getBranch());

        VcsRepositoryUri solutionUrl = exercise.getVcsSolutionRepositoryUri();
        setupGitLabCIConfiguration(solutionUrl, exercise, exercise.getSolutionBuildPlanId());
        // TODO: triggerBuild(solutionUrl, exercise.getBranch());
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
        setupGitLabCIConfiguration(participation.getVcsRepositoryUri(), participation.getProgrammingExercise(), participation.getBuildPlanId());
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

    @Override
    public CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        throw new UnsupportedOperationException("Method not implemented, consult the build plans in GitLab for more information on the checkout directories.");
    }
}
