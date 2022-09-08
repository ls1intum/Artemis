package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.net.URL;
import java.util.*;

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
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

@Profile("gitlabci")
@Service
public class GitLabCIService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIService.class);

    private static final String VARIABLE_DOCKER_IMAGE_NAME = "ARTEMIS_DOCKER_IMAGE";

    private static final String VARIABLE_BRANCH_NAME = "ARTEMIS_BRANCH";

    private static final String VARIABLE_ARTEMIS_SERVER_URL_NAME = "ARTEMIS_SERVER_URL";

    private final GitLabApi gitlab;

    private final UrlService urlService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final BuildPlanRepository buildPlanRepository;

    private final GitLabCIBuildPlanService buildPlanService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${server.url}")
    private URL artemisServerUrl;

    public GitLabCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UrlService urlService, ProgrammingExerciseRepository programmingExerciseRepository,
            BuildPlanRepository buildPlanRepository, GitLabCIBuildPlanService buildPlanService, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, restTemplate, shortTimeoutRestTemplate);
        this.gitlab = gitlab;
        this.urlService = urlService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.buildPlanRepository = buildPlanRepository;
        this.buildPlanService = buildPlanService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        addBuildPlanToProgrammingExerciseIfUnset(exercise);
        setupGitLabCIConfiguration(repositoryURL, exercise);
        // TODO: triggerBuild(repositoryURL);
    }

    private void setupGitLabCIConfiguration(VcsRepositoryUrl repositoryURL, ProgrammingExercise exercise) {
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
            // TODO: Update variables
            projectApi.createVariable(repositoryPath, VARIABLE_DOCKER_IMAGE_NAME,
                    programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType())), Variable.Type.ENV_VAR, false,
                    true);
            projectApi.createVariable(repositoryPath, VARIABLE_BRANCH_NAME, exercise.getBranch(), Variable.Type.ENV_VAR, false, true);
            projectApi.createVariable(repositoryPath, VARIABLE_ARTEMIS_SERVER_URL_NAME, artemisServerUrl.toExternalForm(), Variable.Type.ENV_VAR, false, true);
        }
        catch (GitLabApiException e) {
            log.error("Error creating variable for " + repositoryURL.toString() + " The variables may already have been created.", e);
        }
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
        setupGitLabCIConfiguration(templateUrl, exercise);
        // TODO: triggerBuild(templateUrl);

        VcsRepositoryUrl solutionUrl = exercise.getVcsSolutionRepositoryUrl();
        setupGitLabCIConfiguration(solutionUrl, exercise);
        // TODO: triggerBuild(solutionUrl);
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        log.error("Unsupported action: GitLabCIService.copyBuildPlan()");
        return null;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        setupGitLabCIConfiguration(participation.getVcsRepositoryUrl(), participation.getProgrammingExercise());
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        log.error("Unsupported action: GitLabCIService.performEmptySetupCommit()");
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        triggerBuild(participation.getVcsRepositoryUrl());
    }

    private void triggerBuild(VcsRepositoryUrl vcsRepositoryUrl) {
        final String repositoryPath = getRepositoryPath(vcsRepositoryUrl);
        try {
            Trigger trigger = gitlab.getPipelineApi().createPipelineTrigger(repositoryPath, "Trigger build");
            gitlab.getPipelineApi().triggerPipeline(repositoryPath, trigger, "Trigger build", null);
            gitlab.getPipelineApi().deletePipelineTrigger(repositoryPath, trigger.getId());
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error triggering the build for " + repositoryPath);
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
        log.error("Unsupported action: GitLabCIService.getPlanKey()");
        return null;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        log.error("Unsupported action: GitLabCIService.convertBuildResult()");
        return null;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // https://docs.gitlab.com/ee/api/pipelines.html#list-project-pipelines
        final String repositoryPath = getRepositoryPath(participation.getVcsRepositoryUrl());
        try {
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

    @Override
    protected void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {
        // TODO
    }

    private String generateBuildPlanURL(ProgrammingExercise exercise) {
        programmingExerciseRepository.generateBuildPlanAccessSecretIfNotExists(exercise);
        return String.format("%s/api/files/attachments/exercise/%s/build-plan?secret=%s", artemisServerUrl, exercise.getId(), exercise.getBuildPlanAccessSecret());
    }
}
