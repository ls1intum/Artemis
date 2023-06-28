package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile("gitlabci | jenkins")
@RestController
@RequestMapping("api/")
public class BuildPlanResource {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BuildPlanRepository buildPlanRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingTriggerService programmingTriggerService;

    public BuildPlanResource(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authorizationCheckService, ProgrammingTriggerService programmingTriggerService) {
        this.buildPlanRepository = buildPlanRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingTriggerService = programmingTriggerService;
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @return the build plan stored in the database
     */
    @GetMapping("/programming-exercises/{exerciseId}/build-plan/for-editor")
    @EnforceAtLeastEditor
    public ResponseEntity<BuildPlan> getBuildPlanForEditor(@PathVariable Long exerciseId) {
        log.debug("REST request to get build plan for programming exercise with id {}", exerciseId);

        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(exerciseId);
        // orElseThrow is safe here since the query above ensures that we find a build plan that is attached to that exercise
        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));

        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        return ResponseEntity.ok().body(buildPlan);
    }

    /**
     * Updates the build plan for the given exercise.
     * <p>
     * Triggers a template and solution build to give feedback if the new build plan works as expected.
     *
     * @param exerciseId The exercise for which the build plan should be updated.
     * @param buildPlan  The new build plan for the exercise.
     * @return The updated build plan.
     */
    @PutMapping("/programming-exercises/{exerciseId}/build-plan")
    @EnforceAtLeastEditor
    public ResponseEntity<BuildPlan> setBuildPlan(@PathVariable Long exerciseId, @RequestBody BuildPlan buildPlan) {
        log.debug("REST request to set build plan for programming exercise with id {}", exerciseId);

        final ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        final BuildPlan createdBuildPlan = buildPlanRepository.setBuildPlanForExercise(buildPlan.getBuildPlan(), programmingExercise);
        programmingExerciseRepository.save(programmingExercise);

        programmingTriggerService.triggerTemplateAndSolutionBuild(programmingExercise.getId());

        return ResponseEntity.ok(createdBuildPlan);
    }
}
