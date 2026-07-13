package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildPlanService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final Optional<BuildPhasesTemplateService> buildPhasesTemplateService;

    private final ProfileService profileService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ProgrammingExerciseBuildPlanService(Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            Optional<BuildPhasesTemplateService> buildPhasesTemplateService, ProfileService profileService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.profileService = profileService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * Creates build plans for a new programming exercise.
     * 1. Create the project for the exercise on the CI Server
     * 2. Create template and solution build plan in this project
     * 3. Configure CI permissions
     * 4. Trigger initial build for template and solution build plan (if the exercise is not imported)
     *
     * @param programmingExercise Programming exercise for the build plans should be generated. The programming
     *                                exercise should contain a fully initialized template and solution participation.
     */
    public void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise) {
        // Get URLs for repos
        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testsRepoUri = programmingExercise.getVcsTestRepositoryUri();
        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();

        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri);
        // solution build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUri, testsRepoUri, solutionRepoUri);

        // trigger BASE and SOLUTION build plans once here
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getTemplateParticipation());
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getSolutionParticipation());
    }

    /**
     * Adds the default build plan to a programming exercise.
     * This normalization is skipped for Jenkins, which uses its own Jenkinsfile-based approach.
     *
     * @param programmingExercise the programming exercise whose build config should be normalized
     * @throws JsonProcessingException when the build config cannot be serialized as JSON
     */
    public void addDefaultBuildPlanConfigForLocalCI(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        if (!profileService.isLocalCIActive() || programmingExercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            return;
        }

        var buildConfig = programmingExercise.getBuildConfig();

        // augment with default template or values
        if (buildPhasesTemplateService.isPresent()) {
            final BuildPhasesTemplateService templateService = buildPhasesTemplateService.orElseThrow();
            List<BuildPhaseDTO> phases = templateService.getDefaultBuildPlanPhasesFor(programmingExercise);
            if (programmingExercise.isExamExercise()) {
                phases = templateService.applyExamDefaults(phases);
            }

            final String dockerImage = templateService.getDefaultDockerImageFor(programmingExercise);

            final BuildPlanPhasesDTO completePlan = new BuildPlanPhasesDTO(phases, dockerImage);
            buildConfig.setBuildPlanConfiguration(completePlan.toBuildPlanConfiguration());
            programmingExerciseBuildConfigRepository.saveAndFlush(buildConfig);
        }
        else {
            log.warn("No build plan phases for the settings of exercise {}", programmingExercise.getId());
        }
    }

    /**
     * This method updates the build plan for the given programming exercise.
     * If LocalCI is not active, it deletes the old build plan and creates a new one if the build plan configuration has changed.
     *
     * @param originalBuildPlanConfiguration the build plan configuration before the update
     * @param updatedProgrammingExercise     the changed programming exercise with its new values
     */
    public void updateBuildPlanForExercise(@Nullable String originalBuildPlanConfiguration, ProgrammingExercise updatedProgrammingExercise) throws JsonProcessingException {
        if (continuousIntegrationService.isEmpty() || Objects.equals(originalBuildPlanConfiguration, updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration())) {
            return;
        }
        // we only update the build plan configuration if it has changed and is not null, otherwise we
        // do not have a valid exercise anymore
        if (updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            if (!profileService.isLocalCIActive()) {
                continuousIntegrationService.get().deleteProject(updatedProgrammingExercise.getProjectKey());
                continuousIntegrationService.get().createProjectForExercise(updatedProgrammingExercise);
                continuousIntegrationService.get().recreateBuildPlansForExercise(updatedProgrammingExercise);
                resetAllStudentBuildPlanIdsForExercise(updatedProgrammingExercise);
            }
        }
        else {
            // if the user does not change the build plan configuration, we have to set the old one again
            updatedProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(originalBuildPlanConfiguration);
        }
    }

    /**
     * Updates the build plan configuration (build phases and Docker image), the build timeout, and the Docker flags of an
     * existing programming exercise from the dedicated build plan editor, without re-running the full programming exercise
     * update.
     * <p>
     * The structured configuration is serialized and stored in the build config. For LocalCI the configuration is
     * interpreted at build time, so persisting it is sufficient; {@link #updateBuildPlanForExercise} recreates the build
     * plans for external CI systems when the configuration changed.
     *
     * @param programmingExercise    the programming exercise whose build config should be updated (with its build config loaded)
     * @param buildPlanConfiguration the new build plan configuration (build phases, Docker image, timeout, and Docker flags)
     * @return the persisted build config
     * @throws JsonProcessingException if the build plan configuration cannot be serialized
     */
    public ProgrammingExerciseBuildConfig updateBuildPlanConfiguration(ProgrammingExercise programmingExercise, UpdateBuildPlanConfigurationDTO buildPlanConfiguration)
            throws JsonProcessingException {
        validateBuildPhaseNames(buildPlanConfiguration.buildPlan().phases());

        var buildConfig = programmingExercise.getBuildConfig();
        final String originalBuildPlanConfiguration = buildConfig.getBuildPlanConfiguration();
        buildConfig.setBuildPlanConfiguration(buildPlanConfiguration.buildPlan().toBuildPlanConfiguration());
        // the structured phases configuration supersedes any legacy build script
        buildConfig.setBuildScript(null);
        buildConfig.setTimeoutSeconds(buildPlanConfiguration.timeoutSeconds());
        buildConfig.setDockerFlags(buildPlanConfiguration.dockerFlags());

        updateBuildPlanForExercise(originalBuildPlanConfiguration, programmingExercise);

        return programmingExerciseBuildConfigRepository.saveAndFlush(buildConfig);
    }

    /**
     * Validates that the build plan contains at least one build phase and that the build phase names are unique
     * (case-insensitively) and do not use reserved names. The name pattern and non-blank constraints are enforced via
     * bean validation on {@link BuildPhaseDTO}.
     * <p>
     * Unlike a build plan configuration that is read from an exercise, where a missing configuration means that the
     * defaults of the exercise apply, the build plan editor always submits the complete build plan. An empty one would
     * leave the exercise without any way to build a submission, so it is rejected here.
     *
     * @param phases the build phases to validate
     */
    private void validateBuildPhaseNames(List<BuildPhaseDTO> phases) {
        if (phases == null || phases.isEmpty()) {
            throw new BadRequestAlertException("A build plan must contain at least one build phase", "buildConfig", "emptyBuildPlan");
        }

        var lowerCaseNames = phases.stream().map(phase -> phase.name().toLowerCase(Locale.ROOT)).toList();
        if (new HashSet<>(lowerCaseNames).size() != lowerCaseNames.size()) {
            throw new BadRequestAlertException("Build phase names must be unique", "buildConfig", "duplicateBuildPhaseName");
        }
        if (lowerCaseNames.stream().anyMatch(BuildPhaseDTO.RESERVED_PHASE_NAMES::contains)) {
            throw new BadRequestAlertException("Build phase names must not use reserved names", "buildConfig", "reservedBuildPhaseName");
        }
    }

    private void resetAllStudentBuildPlanIdsForExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseStudentParticipationRepository.unsetBuildPlanIdForExercise(programmingExercise.getId());
    }

}
