package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;

/**
 * REST controller for deleting/resetting programming exercises or related entities such as exercise tasks.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseDeletionResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    public ProgrammingExerciseDeletionResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, Optional<ContinuousIntegrationService> continuousIntegrationService, ExerciseService exerciseService,
            ExerciseDeletionService exerciseDeletionService, ProgrammingExerciseDeletionService programmingExerciseDeletionService, ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * DELETE /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param exerciseId                the id of the programmingExercise to delete
     * @param deleteBaseReposBuildPlans boolean which states whether the base repos and build plans should be deleted as well, this is true by default because for LocalVC and
     *                                      LocalCI, it does not make sense to keep these artifacts
     * @return the ResponseEntity with status 200 (OK) when programming exercise has been successfully deleted or with status 404 (Not Found)
     */
    @DeleteMapping("programming-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable long exerciseId, @RequestParam(defaultValue = "true") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete ProgrammingExercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
        exerciseService.logDeletion(programmingExercise, programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, deleteBaseReposBuildPlans);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, programmingExercise.getTitle())).build();
    }

    /**
     * Reset a programming exercise by performing a set of operations as specified in the
     * ProgrammingExerciseResetOptionsDTO for an exercise given an exerciseId.
     * <p>
     * The available operations include:
     * 1. deleteParticipationsSubmissionsAndResults: Deleting all participations, submissions, and results (also deletes repositories and build plans).
     * 2. recreateBuildPlans: Deleting and recreating the BASE and SOLUTION build plans (for LocalCI / Aeolus, this will reset the customized build plans).
     *
     * @param exerciseId                         - Id of the programming exercise to reset.
     * @param programmingExerciseResetOptionsDTO - Data Transfer Object specifying which operations to perform during the exercise reset.
     * @return ResponseEntity<Void> - The ResponseEntity with status 200 (OK) if the reset was successful.
     */
    @PutMapping("programming-exercises/{exerciseId}/reset")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId, @RequestBody ProgrammingExerciseResetOptionsDTO programmingExerciseResetOptionsDTO)
            throws JsonProcessingException {
        log.debug("REST request to reset programming exercise {} with options {}", exerciseId, programmingExerciseResetOptionsDTO);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigElseThrow(exerciseId);
        final var user = userRepository.getUserWithGroupsAndAuthorities();

        if (programmingExerciseResetOptionsDTO.recreateBuildPlans()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
            continuousIntegrationService.orElseThrow().recreateBuildPlansForExercise(programmingExercise);
        }

        if (programmingExerciseResetOptionsDTO.deleteParticipationsSubmissionsAndResults()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
            exerciseDeletionService.reset(programmingExercise);
            exerciseDeletionService.cleanup(exerciseId);
        }
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE programming-exercises/:exerciseId/tasks : Delete all tasks for an existing ProgrammingExercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 204},
     *         or with status {@code 400 (Bad Request) if the exerciseId is not valid}.
     */
    @DeleteMapping("programming-exercises/{exerciseId}/tasks")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteTasks(@PathVariable Long exerciseId) {
        log.debug("REST request to delete tasks for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        programmingExerciseDeletionService.deleteTasks(exercise.getId());
        exerciseVersionService.createExerciseVersion(exercise);
        return ResponseEntity.noContent().build();
    }

}
