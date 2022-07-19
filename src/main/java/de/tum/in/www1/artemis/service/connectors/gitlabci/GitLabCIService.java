package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.GitLabCIException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

@Profile("gitlabci")
@Service
public class GitLabCIService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIService.class);

    // TODO: Generate an URL and make it accessible via the API
    public static final String CI_CONFIG_URL = "https://home.in.tum.de/~scbe/Artemis/gitlabci-runners/getting-started-template.yml";

    private final GitLabApi gitlab;

    private final UrlService urlService;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    public GitLabCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UrlService urlService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, restTemplate, shortTimeoutRestTemplate);
        this.gitlab = gitlab;
        this.urlService = urlService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        addBuildPlanToGitLabRepositoryConfiguration(repositoryURL);
    }

    private void addBuildPlanToGitLabRepositoryConfiguration(VcsRepositoryUrl repositoryURL) {
        final String repositoryPath = getRepositoryPath(repositoryURL);
        try {
            Project project = gitlab.getProjectApi().getProject(repositoryPath);

            project.setJobsEnabled(true);
            project.setSharedRunnersEnabled(true);
            project.setAutoDevopsEnabled(false);
            project.setCiConfigPath(GitLabCIService.CI_CONFIG_URL);

            gitlab.getProjectApi().updateProject(project);
        }
        catch (GitLabApiException e) {
            throw new GitLabCIException("Error getting project from repository path for " + repositoryURL.toString(), e);
        }
    }

    private String getRepositoryPath(VcsRepositoryUrl repositoryUrl) {
        return urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        addBuildPlanToGitLabRepositoryConfiguration(exercise.getRepositoryURL(RepositoryType.TEMPLATE));
        addBuildPlanToGitLabRepositoryConfiguration(exercise.getRepositoryURL(RepositoryType.SOLUTION));
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        log.error("Unsupported action: GitLabCIService.copyBuildPlan()");
        return null;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        addBuildPlanToGitLabRepositoryConfiguration(participation.getVcsRepositoryUrl());
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        log.error("Unsupported action: GitLabCIService.performEmptySetupCommit()");
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        final String repositoryPath = getRepositoryPath(participation.getVcsRepositoryUrl());
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
        log.error("Please refer to the repository for deleting the project. The build plan can not be deleted seperatly.");
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        log.error("Unsupported action: GitLabCIService.deleteBuildPlan()");
        log.error("Please refer to the repository for deleting the project. The build plan can not be deleted seperatly.");
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
        log.error("Unsupported action: GitLabCIService.getBuildStatus()");
        // TODO
        return null;
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
}
