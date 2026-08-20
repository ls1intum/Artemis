package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * Implementation of ContinuousIntegrationService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem was designed with Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIService implements ContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    private final DistributedDataAccessService distributedDataAccessService;

    public LocalCIService(BuildPhasesTemplateService buildPhasesTemplateService, DistributedDataAccessService distributedDataAccessService) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {
        // Not implemented for local CI. no build plans must be created, because all the information for building
        // a submission and running tests is contained in the participation.
    }

    /**
     * Fetches the default build plan configuration for the given localci exercise
     *
     * @param exercise for which the build plans should be recreated
     */
    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException {
        // LocalCI has no remote build plans; recreating them means resetting the persisted build configuration to the default.
        buildPhasesTemplateService.resetBuildConfigToDefault(exercise);
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
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
        if (!distributedDataAccessService.getQueuedJobsForParticipation(participation.getId()).isEmpty()) {
            return BuildStatus.QUEUED;
        }
        else if (!distributedDataAccessService.getProcessingJobsForParticipation(participation.getId()).isEmpty()) {
            return BuildStatus.BUILDING;
        }
        else {
            return BuildStatus.INACTIVE;
        }
    }

    @Override
    public String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        // No build plans exist for local CI. Only return a plan name.
        final String cleanPlanName = getCleanPlanName(targetPlanName);
        return targetExercise.getProjectKey() + "-" + cleanPlanName;
    }

    @Override
    public void enablePlan(String projectKey, String planKey) throws LocalCIException {
        // Not implemented for local CI. No plans exist that must be enabled.
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri,
            String newBranch) throws LocalCIException {
        // Not implemented for local CI. No build plans exist.
        // When a student pushes to a repository, a build is triggered using the information contained in the participation which includes the relevant repository.
    }

    /**
     * Extract the plan key from the requestBody.
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key or null if it can't be found.
     * @throws LocalCIException is thrown on casting errors.
     */
    @Override
    public String getPlanKey(Object requestBody) throws LocalCIException {
        // This method is called in the processNewProgrammingExerciseResult method of the ResultResource.
        // It is thus never called for local CI as the local CI results directly go to the method processNewProgrammingExerciseResult in the ProgrammingExerciseGradingService.
        return null;
    }

    @Override
    public ConnectorHealth health() {
        // Return a simplified view of build agents for health check
        // This excludes sensitive/large data like build scripts, repository URIs, SSH keys
        var buildAgentsSummary = distributedDataAccessService.getBuildAgentInformation().stream().map(agent -> {
            var buildAgent = agent.buildAgent();
            var name = buildAgent.name() != null ? buildAgent.name() : "Unknown";
            var displayName = buildAgent.displayName() != null ? buildAgent.displayName() : name;
            var memberAddress = buildAgent.memberAddress() != null ? buildAgent.memberAddress() : "";
            var status = agent.status() != null ? agent.status().name() : "UNKNOWN";
            var runningJobs = agent.runningBuildJobs().stream().map(job -> job.name() != null ? job.name() : job.id()).map(String::valueOf).toList();
            return Map.of("name", name, "displayName", displayName, "memberAddress", memberAddress, "status", status, "maxJobs", agent.maxNumberOfConcurrentBuildJobs(),
                    "currentJobs", agent.numberOfCurrentBuildJobs(), "runningJobs", runningJobs);
        }).toList();
        return new ConnectorHealth(true, Map.of("buildAgents", buildAgentsSummary));
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        // For local CI, no projects exist. Therefore, we always return null.
        return null;
    }

    /**
     * Check if the given build plan is valid and accessible on the local CI system.
     *
     * @param projectKey  the key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if the build plan exists.
     */
    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        // For local CI, no build plans exist. Always return true since builds are triggered via participation.
        return true;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
