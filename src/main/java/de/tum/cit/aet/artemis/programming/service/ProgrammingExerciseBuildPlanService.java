package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildPlanService.class);

    // private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<BuildScriptGenerationService> buildScriptGenerationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final ProfileService profileService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ProgrammingExerciseBuildPlanService(Optional<BuildScriptGenerationService> buildScriptGenerationService,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            Optional<AeolusTemplateService> aeolusTemplateService, ProfileService profileService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        // this.continuousIntegrationService = continuousIntegrationService;
        this.buildScriptGenerationService = buildScriptGenerationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.aeolusTemplateService = aeolusTemplateService;
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
    public void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        // Get URLs for repos
        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testsRepoUri = programmingExercise.getVcsTestRepositoryUri();
        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();

        // ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        // continuousIntegration.createProjectForExercise(programmingExercise);
        // // template build plan
        // continuousIntegration.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri);
        // // solution build plan
        // continuousIntegration.createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUri, testsRepoUri, solutionRepoUri);

        Windfile windfile = programmingExercise.getBuildConfig().getWindfile();
        if (windfile != null && buildScriptGenerationService.isPresent() && programmingExercise.getBuildConfig().getBuildScript() == null) {
            String script = buildScriptGenerationService.get().getScript(programmingExercise);
            programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
            programmingExercise.getBuildConfig().setBuildScript(script);
            programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
        }

        // trigger BASE and SOLUTION build plans once here
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getTemplateParticipation());
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getSolutionParticipation());
    }

    /**
     * Adds a default build plan config if LocalCI is active and no build plan exists
     *
     * @param programmingExercise the programming exercise the default build config should be added
     * @throws JsonProcessingException when the default build config cannot be written as JSON string
     */
    public void addDefaultBuildPlanConfigForLocalCI(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        // For LocalCI and Aeolus, we store the build plan definition in the database as a windfile, we don't do that for Jenkins as
        // we want to use the default approach of Jenkinsfiles and Build Plans if no customizations are made
        if (aeolusTemplateService.isPresent() && programmingExercise.getBuildConfig().getBuildPlanConfiguration() == null && !profileService.isJenkinsActive()) {
            Windfile windfile = aeolusTemplateService.get().getDefaultWindfileFor(programmingExercise);
            if (windfile != null) {
                programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
                programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
            }
            else {
                log.warn("No windfile for the settings of exercise {}", programmingExercise.getId());
            }
        }
    }

    /**
     * This method updates the build plan for the given programming exercise.
     * If LocalCI is not active, it deletes the old build plan and creates a new one if the build plan configuration has changed.
     *
     * @param programmingExerciseBeforeUpdate the original programming exercise with its old values
     * @param updatedProgrammingExercise      the changed programming exercise with its new values
     */
    public void updateBuildPlanForExercise(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise updatedProgrammingExercise) throws JsonProcessingException {
        if (Objects.equals(programmingExerciseBeforeUpdate.getBuildConfig().getBuildPlanConfiguration(), updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration())) {
            return;
        }
        // we only update the build plan configuration if it has changed and is not null, otherwise we
        // do not have a valid exercise anymore
        if (updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            if (!profileService.isLocalCIActive()) {
                // continuousIntegrationService.get().deleteProject(updatedProgrammingExercise.getProjectKey());
                // continuousIntegrationService.get().createProjectForExercise(updatedProgrammingExercise);
                // continuousIntegrationService.get().recreateBuildPlansForExercise(updatedProgrammingExercise);
                resetAllStudentBuildPlanIdsForExercise(updatedProgrammingExercise);
            }
            // For Aeolus, we have to regenerate the build script based on the new Windfile of the exercise.
            // We skip this for pure LocalCI to prevent the build script from being overwritten by the default one.
            if (profileService.isAeolusActive() && buildScriptGenerationService.isPresent()) {
                String script = buildScriptGenerationService.get().getScript(updatedProgrammingExercise);
                updatedProgrammingExercise.getBuildConfig().setBuildScript(script);
                programmingExerciseBuildConfigRepository.save(updatedProgrammingExercise.getBuildConfig());
            }
        }
        else {
            // if the user does not change the build plan configuration, we have to set the old one again
            updatedProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(programmingExerciseBeforeUpdate.getBuildConfig().getBuildPlanConfiguration());
        }
    }

    private void resetAllStudentBuildPlanIdsForExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseStudentParticipationRepository.unsetBuildPlanIdForExercise(programmingExercise.getId());
    }

}
