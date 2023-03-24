package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ci.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Profile("jenkins")
@Service
public class JenkinsService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    private final JenkinsServer jenkinsServer;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsInternalUrlService jenkinsInternalUrlService;

    private final RestTemplate shortTimeoutRestTemplate;

    public JenkinsService(JenkinsServer jenkinsServer, ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate, BuildLogEntryService buildLogService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, JenkinsBuildPlanService jenkinsBuildPlanService, JenkinsJobService jenkinsJobService,
            JenkinsInternalUrlService jenkinsInternalUrlService, TestwiseCoverageService testwiseCoverageService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService);
        this.jenkinsServer = jenkinsServer;
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
        this.jenkinsJobService = jenkinsJobService;
        this.jenkinsInternalUrlService = jenkinsInternalUrlService;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, planKey, repositoryURL);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        final String projectKey = exercise.getProjectKey();

        if (!jenkinsBuildPlanService.projectFolderExists(projectKey)) {
            createProjectForExercise(exercise);
        }

        deleteBuildPlan(projectKey, exercise.getTemplateBuildPlanId());
        deleteBuildPlan(projectKey, exercise.getSolutionBuildPlanId());

        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getRepositoryURL(RepositoryType.TEMPLATE));
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getRepositoryURL(RepositoryType.SOLUTION));
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for Jenkins
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        return jenkinsBuildPlanService.copyBuildPlan(sourceProjectKey, sourcePlanName, targetProjectKey, targetPlanName);
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        jenkinsBuildPlanService.givePlanPermissions(programmingExercise, planName);
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
        jenkinsBuildPlanService.configureBuildPlanForParticipation(participation);
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newDefaultBranch, List<String> triggeredByRepositories) {
        jenkinsBuildPlanService.updateBuildPlanRepositories(buildProjectKey, buildPlanKey, newRepoUrl, existingRepoUrl);
    }

    @Override
    public void deleteProject(String projectKey) {
        jenkinsJobService.deleteJob(projectKey);
    }

    @Override
    public void deleteBuildPlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.deleteBuildPlan(projectKey, planKey);
    }

    @Override
    public String getPlanKey(Object requestBody) throws JenkinsException {
        try {
            TestResultsDTO dto = TestResultsDTO.convert(requestBody);
            return jenkinsBuildPlanService.getBuildPlanKeyFromTestResults(dto);
        }
        catch (JsonProcessingException jsonProcessingException) {
            throw new JenkinsException("Something went wrong trying to parse the requestBody while getting the PlanKey from Jenkins!");
        }
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        var urlString = serverUrl + "/project/" + projectKey + "/" + buildPlanId;
        return Optional.of(jenkinsInternalUrlService.toInternalCiUrl(urlString));
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        if (participation.getBuildPlanId() == null) {
            // The build plan does not exist, the build status cannot be retrieved
            return null;
        }

        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        return jenkinsBuildPlanService.getBuildStatusOfPlan(projectKey, planKey);
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return jenkinsBuildPlanService.buildPlanExists(projectKey, buildPlanId);
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO, not necessary for the core functionality
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            final var job = jenkinsServer.getJob(projectKey);
            if (job == null) {
                // means the project does not exist
                return null;
            }
            else if (job.getUrl() == null || job.getUrl().isEmpty()) {
                return null;
            }
            else {
                return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
            }
        }
        catch (Exception emAll) {
            log.warn(emAll.getMessage());
            // in case of an error message, we assume the project exist (like in Bamboo service)
            return "The project already exists on the Continuous Integration Server. Please choose a different title and short name!";
        }
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.enablePlan(projectKey, planKey);
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public ConnectorHealth health() {
        try {
            // Note: we simply check if the login page is reachable
            shortTimeoutRestTemplate.getForObject(serverUrl + "/login", String.class);
            return new ConnectorHealth(true, Map.of("url", serverUrl));
        }
        catch (Exception emAll) {
            var health = new ConnectorHealth(false, Map.of("url", serverUrl));
            health.setException(new JenkinsException("Jenkins Server is down!"));
            return health;
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        try {
            jenkinsServer.createFolder(programmingExercise.getProjectKey(), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }
}
