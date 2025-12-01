package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;

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

    private final BuildScriptProviderService buildScriptProviderService;

    private final AeolusTemplateService aeolusTemplateService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public LocalCIService(BuildScriptProviderService buildScriptProviderService, AeolusTemplateService aeolusTemplateService,
            DistributedDataAccessService distributedDataAccessService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.buildScriptProviderService = buildScriptProviderService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri) {
        // Not implemented for local CI. no build plans must be created, because all the information for building
        // a submission and running tests is contained in the participation.
    }

    /**
     * Fetches the default build plan configuration for the given exercise and the windfile for its metadata (docker image etc.).
     *
     * @param exercise for which the build plans should be recreated
     */
    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException {
        // TODO: implement this differently for LocalCI in the future
        if (exercise == null) {
            return;
        }
        log.debug("Recreating build plans for exercise {}", exercise.getTitle());
        String script = buildScriptProviderService.getScriptFor(exercise);
        Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(exercise);
        ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
        // TODO: This is almost certainly wrong like this! We need to enable recreating build plans for exercises with multiple containers. Maybe we delete the other containers?
        buildConfig.getDefaultContainerConfig().setBuildScript(script);
        buildConfig.getDefaultContainerConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
        // recreating the build plans for the exercise means we need to store the updated build config in the database
        programmingExerciseBuildConfigRepository.save(buildConfig);
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
        return new ConnectorHealth(true, Map.of("buildAgents", distributedDataAccessService.getBuildAgentInformation()));
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
        // TODO: we should check that the build script in the programming exercises exists, otherwise builds will fail
        // For local CI, no build plans exist. This method is always used in a context where build plans should exist and an error is thrown if they don't.
        // It is safe here to always return true.
        return true;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
