package de.tum.cit.aet.artemis.service.connectors.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.service.connectors.BuildScriptProviderService;
import de.tum.cit.aet.artemis.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.service.connectors.ci.AbstractContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.ci.CIPermission;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.web.rest.dto.BuildPlanCheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.web.rest.dto.CheckoutDirectoriesDTO;

/**
 * Implementation of ContinuousIntegrationService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem and the AbstractContinuousIntegrationService were designed with Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final BuildScriptProviderService buildScriptProviderService;

    private final AeolusTemplateService aeolusTemplateService;

    private final SharedQueueManagementService sharedQueueManagementService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public LocalCIService(BuildScriptProviderService buildScriptProviderService, AeolusTemplateService aeolusTemplateService,
            SharedQueueManagementService sharedQueueManagementService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.buildScriptProviderService = buildScriptProviderService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.sharedQueueManagementService = sharedQueueManagementService;
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
        if (exercise == null) {
            return;
        }
        String script = buildScriptProviderService.getScriptFor(exercise);
        Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(exercise);
        ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
        buildConfig.setBuildScript(script);
        buildConfig.setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
        // recreating the build plans for the exercise means we need to store the updated build config in the database
        programmingExerciseBuildConfigRepository.save(buildConfig);
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
        if (!sharedQueueManagementService.getQueuedJobsForParticipation(participation.getId()).isEmpty()) {
            return BuildStatus.QUEUED;
        }
        else if (!sharedQueueManagementService.getProcessingJobsForParticipation(participation.getId()).isEmpty()) {
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
        return new ConnectorHealth(true, Map.of("buildAgents", sharedQueueManagementService.getBuildAgentInformation()));
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        // No webhooks needed within Integrated Code Lifecycle, so we return an empty Optional.
        return Optional.empty();
    }

    /**
     * Gets the latest available artifact for the given participation.
     *
     * @param participation to use its buildPlanId to find the artifact.
     * @return the html representation of the artifact page.
     */
    @Override
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
        // TODO: we should check that the build script in the programming exercises exists, otherwise builds will fail
        // For local CI, no build plans exist. This method is always used in a context where build plans should exist and an error is thrown if they don't.
        // It is safe here to always return true.
        return true;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    @Override
    public CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        BuildPlanCheckoutDirectoriesDTO submissionBuildPlanCheckoutDirectories = getSubmissionBuildPlanCheckoutDirectories(programmingLanguage, checkoutSolution);
        BuildPlanCheckoutDirectoriesDTO solutionBuildPlanCheckoutDirectories = getSolutionBuildPlanCheckoutDirectories(submissionBuildPlanCheckoutDirectories);

        return new CheckoutDirectoriesDTO(submissionBuildPlanCheckoutDirectories, solutionBuildPlanCheckoutDirectories);
    }

    private BuildPlanCheckoutDirectoriesDTO getSubmissionBuildPlanCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        String exerciseCheckoutDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        String testCheckoutDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);

        exerciseCheckoutDirectory = startPathWithRootDirectory(exerciseCheckoutDirectory);
        testCheckoutDirectory = startPathWithRootDirectory(testCheckoutDirectory);

        String solutionCheckoutDirectory = null;

        if (checkoutSolution) {
            try {
                String solutionCheckoutDirectoryPath = ContinuousIntegrationService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
                solutionCheckoutDirectory = startPathWithRootDirectory(solutionCheckoutDirectoryPath);
            }
            catch (IllegalArgumentException exception) {
                // not checked out during template & submission build
            }
        }

        return new BuildPlanCheckoutDirectoriesDTO(exerciseCheckoutDirectory, solutionCheckoutDirectory, testCheckoutDirectory);
    }

    private String startPathWithRootDirectory(String checkoutDirectoryPath) {
        final String ROOT_DIRECTORY = "/";
        if (checkoutDirectoryPath == null || checkoutDirectoryPath.isEmpty()) {
            return ROOT_DIRECTORY;
        }

        return checkoutDirectoryPath.startsWith(ROOT_DIRECTORY) ? checkoutDirectoryPath : ROOT_DIRECTORY + checkoutDirectoryPath;
    }

    private BuildPlanCheckoutDirectoriesDTO getSolutionBuildPlanCheckoutDirectories(BuildPlanCheckoutDirectoriesDTO submissionBuildPlanCheckoutDirectories) {
        String solutionCheckoutDirectory = submissionBuildPlanCheckoutDirectories.exerciseCheckoutDirectory();
        String testCheckoutDirectory = submissionBuildPlanCheckoutDirectories.testCheckoutDirectory();

        return new BuildPlanCheckoutDirectoriesDTO(null, solutionCheckoutDirectory, testCheckoutDirectory);
    }
}
