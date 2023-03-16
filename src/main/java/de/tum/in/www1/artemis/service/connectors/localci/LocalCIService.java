package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

/**
 * Implementation of ContinuousIntegrationService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem and the AbstractContinuousIntegrationService were designed with Bamboo and Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Service
@Profile("localci")
public class LocalCIService implements ContinuousIntegrationService {

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUrl sourceCodeRepositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        // For Bamboo and Jenkins, this method is called for the template and the solution repository and creates and publishes a new build plan which results in a new build being
        // triggered.
        // For local CI, a build plan must not be created, because all the information for building a submission and running tests is contained in the participation.
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
        // Empty implementation. Not needed for local CI.
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for local CI. Implemented for Bamboo as a bug workaround.
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
        if (participation.getBuildPlanId().endsWith(BuildStatus.QUEUED.name())) {
            return BuildStatus.QUEUED;
        }
        else if (participation.getBuildPlanId().endsWith(BuildStatus.BUILDING.name())) {
            return BuildStatus.BUILDING;
        }
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
            String newBranch, Optional<List<String>> optionalTriggeredByRepositories) throws LocalCIException {
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
        // Build artifacts are not supported by local CI yet.
        return ResponseEntity.noContent().build();
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
