package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;

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
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseBuildPlanService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<BuildScriptGenerationService> buildScriptGenerationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final ProfileService profileService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ProgrammingExerciseBuildPlanService(Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<BuildScriptGenerationService> buildScriptGenerationService, Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, Optional<AeolusTemplateService> aeolusTemplateService, ProfileService profileService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.continuousIntegrationService = continuousIntegrationService;
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

        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri);
        // solution build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUri, testsRepoUri, solutionRepoUri);

        // TODO: Idk if we should just use the default here everywhere. Investage later, plz future me. Probably this is wrong.
        Windfile windfile = programmingExercise.getBuildConfig().getDefaultWindfile();
        if (windfile != null && buildScriptGenerationService.isPresent() && programmingExercise.getBuildConfig().getDefaultContainerConfig().getBuildScript() == null) {
            String script = buildScriptGenerationService.get().getScript(programmingExercise);
            programmingExercise.getBuildConfig().getDefaultContainerConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
            programmingExercise.getBuildConfig().getDefaultContainerConfig().setBuildScript(script);
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
        // TODO: Investigate the user of default stuff here too.
        if (aeolusTemplateService.isPresent() && programmingExercise.getBuildConfig().getDefaultContainerConfig().getBuildPlanConfiguration() == null
                && !profileService.isJenkinsActive()) {
            Windfile windfile = aeolusTemplateService.get().getDefaultWindfileFor(programmingExercise);
            if (windfile != null) {
                programmingExercise.getBuildConfig().getDefaultContainerConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
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
        // TODO: Investigate the user of default stuff here too.
        if (continuousIntegrationService.isEmpty() || Objects.equals(programmingExerciseBeforeUpdate.getBuildConfig().getDefaultContainerConfig().getBuildPlanConfiguration(),
                updatedProgrammingExercise.getBuildConfig().getDefaultContainerConfig().getBuildPlanConfiguration())) {
            return;
        }
        // we only update the build plan configuration if it has changed and is not null, otherwise we
        // do not have a valid exercise anymore
        if (updatedProgrammingExercise.getBuildConfig().getDefaultContainerConfig().getBuildPlanConfiguration() != null) {
            if (!profileService.isLocalCIActive()) {
                continuousIntegrationService.get().deleteProject(updatedProgrammingExercise.getProjectKey());
                continuousIntegrationService.get().createProjectForExercise(updatedProgrammingExercise);
                continuousIntegrationService.get().recreateBuildPlansForExercise(updatedProgrammingExercise);
                resetAllStudentBuildPlanIdsForExercise(updatedProgrammingExercise);
            }
            // For Aeolus, we have to regenerate the build script based on the new Windfile of the exercise.
            // We skip this for pure LocalCI to prevent the build script from being overwritten by the default one.
            if (profileService.isAeolusActive() && buildScriptGenerationService.isPresent()) {
                String script = buildScriptGenerationService.get().getScript(updatedProgrammingExercise);
                updatedProgrammingExercise.getBuildConfig().getDefaultContainerConfig().setBuildScript(script);
                programmingExerciseBuildConfigRepository.save(updatedProgrammingExercise.getBuildConfig());
            }
        }
        else {
            // if the user does not change the build plan configuration, we have to set the old one again
            updatedProgrammingExercise.getBuildConfig().getDefaultContainerConfig()
                    .setBuildPlanConfiguration(programmingExerciseBeforeUpdate.getBuildConfig().getDefaultContainerConfig().getBuildPlanConfiguration());
        }
    }

    private void resetAllStudentBuildPlanIdsForExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseStudentParticipationRepository.unsetBuildPlanIdForExercise(programmingExercise.getId());
    }

}
