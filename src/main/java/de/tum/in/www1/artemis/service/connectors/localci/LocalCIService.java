package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ci.CIPermission;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

/**
 * Implementation of ContinuousIntegrationService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem and the AbstractContinuousIntegrationService were designed with Bamboo and Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Service
@Profile("localci")
public class LocalCIService extends AbstractContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final LocalCITriggerService localCITriggerService;

    private final LocalCIDockerService localCIDockerService;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    public LocalCIService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository, BuildLogEntryService buildLogService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService, LocalCITriggerService localCITriggerService,
            LocalCIDockerService localCIDockerService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryRepository, testwiseCoverageService);
        this.localCITriggerService = localCITriggerService;
        this.localCIDockerService = localCIDockerService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUrl sourceCodeRepositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        // For Bamboo and Jenkins, this method is called for the template and the solution repository and creates and publishes a new build plan
        // which results in a new build being triggered.
        // For local CI, a build plan must not be created, because all the information for building a submission and running tests is contained in the participation, so we only
        // trigger the build here.

        // Check if docker image exists locally, otherwise pull it from Docker Hub.

        localCIDockerService.pullDockerImage(dockerImage);

        // Trigger build for the given participation.

        if (TEMPLATE.getName().equals(planKey)) {
            localCITriggerService.triggerBuild(programmingExercise.getTemplateParticipation());
        }
        else if (SOLUTION.getName().equals(planKey)) {
            localCITriggerService.triggerBuild(programmingExercise.getSolutionParticipation());
        }
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        // Not implemented for local CI. no build plans must be (re)created, because all the information for building a submission and running tests is contained in the
        // participation.
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
        // Empty implementation. Not needed for local CI.
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        // Not needed for local CI. Build plans are grouped into projects automatically.
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        // Not implemented for local CI. No build plans exist and thus no build plans can be deleted.
    }

    /**
     * Delete project with given identifier.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    @Override
    public void deleteProject(String projectKey) {
        // Not implemented for local CI. No build plans exist and thus no projects exist that contain build plans.
    }

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // TODO: Retrieve the correct status from the database once the table is implemented.
        return BuildStatus.INACTIVE;
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        // No build plans exist for local CI. Only return a plan name.
        final String cleanPlanName = getCleanPlanName(targetPlanName);
        return targetProjectKey + "-" + cleanPlanName;
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
        // Not implemented for local CI. No plans exist that must be enabled.
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newBranch) throws LocalCIException {
        // Not implemented for local CI. No build plans exist.
        // When a student pushes to a repository, a build is triggered using the information contained in the participation which includes the relevant repository.
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
        // This method is called in the processNewProgrammingExerciseResult method of the ResultResource.
        // It is thus never called for local CI as the local CI results directly go to the method processNewProgrammingExerciseResult in the ProgrammingExerciseGradingService.
        return null;
    }

    @Override
    public ConnectorHealth health() {
        return new ConnectorHealth(true);
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        // No webhooks needed between local CI and local VC, so we return an empty Optional.
        return Optional.empty();
    }

    /**
     * Gets the latest available artifact for the given participation.
     *
     * @param participation to use its buildPlanId to find the artifact.
     * @return the html representation of the artifact page.
     */
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO LOCALVC_CI: Extract artifacts from the container when running the build job, store them on disk, and retrieve them here.
        log.error("Unsupported action: LocalCIService.retrieveLatestArtifact()");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new byte[0]);
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        // For local CI, no projects exist. Therefore, we always return null.
        return null;
    }

    /**
     * Check if the given build plan is valid and accessible on the local CI system.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if the build plan exists.
     */
    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        // For local CI, no build plans exist. This method is always used in a context where build plans should exist and an error is thrown if they don't.
        // It is safe here to always return true.
        return true;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
