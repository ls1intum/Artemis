package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.aeolus.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

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
     * Ensures that the build plan configuration for a programming exercise is stored in the
     * {@link BuildPlanPhasesDTO} format. This handles three cases:
     * <ol>
     * <li>No config exists ({@code null}): loads the default windfile template and converts it to phases format</li>
     * <li>Config exists in legacy Windfile format: converts it to phases format</li>
     * <li>Config already in phases format: no change needed</li>
     * </ol>
     * This normalization is skipped for Jenkins, which uses its own Jenkinsfile-based approach.
     *
     * @param programmingExercise the programming exercise whose build config should be normalized
     * @throws JsonProcessingException when the build config cannot be serialized as JSON
     */
    public void addDefaultBuildPlanConfigForLocalCI(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        if (profileService.isJenkinsActive()) {
            return;
        }

        var buildConfig = programmingExercise.getBuildConfig();

        // already in phases format, nothing to do
        if (buildConfig.getBuildPlanPhases().isPresent()) {
            return;
        }

        BuildPlanPhasesDTO buildPlanPhasesDTO = null;

        // no config at all, load default build plan phases template
        if (buildConfig.getBuildPlanConfiguration() == null && buildPhasesTemplateService.isPresent()) {
            buildPlanPhasesDTO = new BuildPlanPhasesDTO(buildPhasesTemplateService.orElseThrow().getDefaultBuildPlanPhasesFor(programmingExercise), null);
        }

        if (buildPlanPhasesDTO != null) {
            buildConfig.setBuildPlanConfiguration(buildPlanPhasesDTO.toBuildPlanConfiguration());
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

    private void resetAllStudentBuildPlanIdsForExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseStudentParticipationRepository.unsetBuildPlanIdForExercise(programmingExercise.getId());
    }

}
