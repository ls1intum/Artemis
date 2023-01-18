package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.*;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.*;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Service
@Profile("localci")
public class LocalCIService extends AbstractContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    private final GitService gitService;

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final ObjectMapper mapper;

    private final UrlService urlService;

    public LocalCIService(GitService gitService, ProgrammingSubmissionRepository programmingSubmissionRepository, LocalCIBuildPlanService localCIBuildPlanService,
            FeedbackRepository feedbackRepository, ObjectMapper mapper, UrlService urlService, BuildLogEntryService buildLogService,
            TestwiseCoverageService testwiseCoverageService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService);
        this.gitService = gitService;
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.mapper = mapper;
        this.urlService = urlService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUrl sourceCodeRepositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        var additionalRepositories = programmingExercise.getAuxiliaryRepositoriesForBuildPlan().stream()
                .map(repo -> new AuxiliaryRepository.AuxRepoNameWithSlug(repo.getName(), urlService.getRepositorySlugFromRepositoryUrl(repo.getVcsRepositoryUrl()))).toList();
        localCIBuildPlanService.createBuildPlanForExercise(programmingExercise, planKey, urlService.getRepositorySlugFromRepositoryUrl(sourceCodeRepositoryURL),
                urlService.getRepositorySlugFromRepositoryUrl(testRepositoryURL), urlService.getRepositorySlugFromRepositoryUrl(solutionRepositoryURL), additionalRepositories);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        deleteBuildPlan(exercise.getProjectKey(), exercise.getTemplateBuildPlanId());
        deleteBuildPlan(exercise.getProjectKey(), exercise.getSolutionBuildPlanId());
        createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getVcsTemplateRepositoryUrl(), exercise.getVcsTestRepositoryUrl(),
                exercise.getVcsSolutionRepositoryUrl());
        createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getVcsSolutionRepositoryUrl(), exercise.getVcsTestRepositoryUrl(),
                exercise.getVcsSolutionRepositoryUrl());
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
        String buildPlanId = participation.getBuildPlanId();
        LocalVCRepositoryUrl repositoryUrl = (LocalVCRepositoryUrl) participation.getVcsRepositoryUrl();
        String projectKey = getProjectKeyFromBuildPlanId(buildPlanId);
        String repoProjectName = repositoryUrl.getProjectKey();
        updatePlanRepository(projectKey, buildPlanId, ASSIGNMENT_REPO_NAME, repoProjectName, participation.getRepositoryUrl(), null /* not needed */, branch, Optional.empty());
        enablePlan(projectKey, buildPlanId);

        // allow student or team access to the build plan in case this option was specified (only available for course exercises)
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        // TODO: Set publishBuildPlanUrl to false for all programming exercises create for local VC and local CI.
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for local CI. Implemented for Bamboo as a bug workaround.
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        // Not needed for local CI. Build plans are grouped into projects automatically.
    }

    /**
     * Triggers a build for the build plan in the given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws LocalCIException {
        var buildPlan = participation.getBuildPlanId();
        // TODO: Empty implementation to not break structure.
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {

        var buildPlan = getBuildPlan(buildPlanId);
        if (buildPlan == null) {
            log.error("Cannot delete {}, because it does not exist!", buildPlanId);
            return;
        }

        executeDelete("selectedBuilds", buildPlanId);
        log.info("Delete bamboo build plan {} was successful.", buildPlanId);
    }

    /**
     * NOTE: the REST call in this method fails silently with a 404 in case all build plans have already been deleted before
     *
     * @param projectKey the project which build plans should be retrieved
     * @return a list of build plans
     */
    private List<LocalCIBuildPlan> getBuildPlans(String projectKey) {
        // TODO: Empty implementation to not break structure.
        return List.of();
    }

    /**
     * Delete project with given identifier.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    @Override
    public void deleteProject(String projectKey) {
        log.info("Trying to delete local CI project {}", projectKey);

        // TODO: check if the project actually exists, if not, we can immediately return

        // in normal cases this list should be empty, because all build plans have been deleted before
        final List<LocalCIBuildPlan> buildPlans = getBuildPlans(projectKey);
        for (var buildPlan : buildPlans) {
            try {
                deleteBuildPlan(projectKey, buildPlan.key());
            }
            catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }

        executeDelete("selectedProjects", projectKey);
        log.info("Deleting local CI project {} was successful.", projectKey);
    }

    private void executeDelete(String elementKey, String elementValue) {
        // TODO: Empty implementation to not break structure.
    }

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // TODO: Empty implementation to not break structure.
        return BuildStatus.INACTIVE;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        // TODO: Empty implementation to not break structure.
        return List.of();
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        // TODO: Empty implementation to not break structure.
    }

    /**
     * get the build plan for the given planKey
     *
     * @param planKey the unique local CI build plan identifier
     * @return the build plan
     */
    private LocalCIBuildPlan getBuildPlan(String planKey) {
        // TODO: Empty implementation to not break structure.
        return null;
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        final var cleanPlanName = getCleanPlanName(targetPlanName);
        final var targetPlanKey = targetProjectKey + "-" + cleanPlanName;
        // TODO: Empty implementation to not break structure.
        return targetPlanKey;
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        // Not implemented for local CI.
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // Not implemented for local CI.
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groupNames, List<CIPermission> permissions) {
        // Not implemented for local CI.
    }

    @Override
    public void enablePlan(String projectKey, String planKey) throws LocalCIException {
        // TODO: Empty implementation to not break structure.
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newBranch, Optional<List<String>> optionalTriggeredByRepositories) throws LocalCIException {
        localCIBuildPlanService.updateBuildPlanRepositories(buildProjectKey, buildPlanKey, newRepoUrl, existingRepoUrl);
    }

    /**
     * Extract the plan key from the Bamboo requestBody.
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key or null if it can't be found.
     * @throws BambooException is thrown on casting errors.
     */
    @Override
    public String getPlanKey(Object requestBody) throws LocalCIException {
        // TODO: Empty implementation to not break structure.
        return null;
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        // TODO: Empty implementation to not break structure.
        return null;
    }

    @Override
    public ConnectorHealth health() {
        return new ConnectorHealth(true);
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        // No webhooks needed between local CI and local VC, so we return an empty Optional
        return Optional.empty();
    }

    /**
     * Gets the latest available artifact for the given participation.
     *
     * @param participation to use its buildPlanId to find the artifact.
     * @return the html representation of the artifact page.
     */
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO: Empty implementation to not break structure.
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        // TODO: Empty implementation to not break structure.
        return "Not implemented yet.";
    }

    /**
     * Check if the given build plan is valid and accessible on the local CI system.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if the build plan exists.
     */
    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return getBuildPlan(buildPlanId.toUpperCase()) != null;
    }

    private String getProjectKeyFromBuildPlanId(String buildPlanId) {
        return buildPlanId.split("-")[0];
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
