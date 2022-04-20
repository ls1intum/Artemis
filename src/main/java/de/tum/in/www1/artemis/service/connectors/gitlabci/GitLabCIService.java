package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.net.URL;
import java.util.List;
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
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

@Profile("gitlabci")
@Service
public class GitLabCIService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIService.class);

    // TODO: Generate an URL and make it accessible via the API
    public static final String CI_CONFIG_URL = "https://home.in.tum.de/~scbe/Artemis/gitlabci-runners/getting-started-template.yml";

    private final GitLabApi gitlab;

    private UrlService urlService;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    public GitLabCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            RestTemplate restTemplate, RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UrlService urlService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, restTemplate, shortTimeoutRestTemplate);
        this.gitlab = gitlab;
        this.urlService = urlService;
    }

    @Override
    protected void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {

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
            throw new GitLabCIException("Error getting project from repository path ", e);
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
        // TODO: Necessary information missing.
        // Database query: SELECT repository_url FROM participation WHERE build_plan_id = '';
        return null;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        addBuildPlanToGitLabRepositoryConfiguration(participation.getVcsRepositoryUrl());
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // TODO: Necessary ?
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        final String repositoryPath = getRepositoryPath(participation.getVcsRepositoryUrl());
        // TODO: Should the trigger token be stored in the database or removed again
        try {
            Trigger trigger = gitlab.getPipelineApi().createPipelineTrigger(repositoryPath, "Trigger build");
            gitlab.getPipelineApi().triggerPipeline(repositoryPath, trigger, "Trigger build", null);
            gitlab.getPipelineApi().deletePipelineTrigger(repositoryPath, trigger.getId());
        }
        catch (GitLabApiException e) {
            // TODO: Should I add the URL / the repository name for debugging; should I add the throwable ?
            log.error("Error while triggering the build", e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        // TODO: Necessary ?
        log.warn("Can not delete a GitLab CI project separately. Delete the whole repository if needed.");
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        // TODO: Necessary ?
        log.warn("Can not delete a GitLab CI project separately. Delete the whole repository if needed.");
    }

    @Override
    public String getPlanKey(Object requestBody) throws ContinuousIntegrationException {
        return null;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        return null;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return false;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        return null;
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        return null;
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {

    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newDefaultBranch, Optional<List<String>> optionalTriggeredByRepositories) {

    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {

    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {

    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {

    }

    @Override
    public ConnectorHealth health() {
        return GitLabService.health(gitlabServerUrl, shortTimeoutRestTemplate);
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {

    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        return Optional.empty();
    }
}
