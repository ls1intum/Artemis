package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.BUILD_PLAN;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile("gitlabci | jenkins")
@RestController
@RequestMapping("api/")
public class BuildPlanResource {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BuildPlanRepository buildPlanRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public BuildPlanResource(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.buildPlanRepository = buildPlanRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @param secret     the secret to authenticate the request
     * @return the build plan stored in the database
     */
    @GetMapping(BUILD_PLAN)
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> getBuildPlan(@PathVariable Long exerciseId, @RequestParam("secret") String secret) {
        log.debug("REST request to get build plan for programming exercise with id {}", exerciseId);

        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(exerciseId);
        // orElseThrow is safe here since the query above ensures that we find a build plan that is attached to that exercise
        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));

        if (!programmingExercise.hasBuildPlanAccessSecretSet() || !secret.equals(programmingExercise.getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }

        return ResponseEntity.ok().body(buildPlan.getBuildPlan());
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @return the build plan stored in the database
     */
    @GetMapping(BUILD_PLAN + "/for-editor")
    @PreAuthorize("permitAll()")
    public ResponseEntity<BuildPlan> getBuildPlanForEditor(@PathVariable Long exerciseId) {
        log.debug("REST request to get build plan for programming exercise with id {}", exerciseId);

        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(exerciseId);
        // orElseThrow is safe here since the query above ensures that we find a build plan that is attached to that exercise
        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));

        // authorization when called from the build plan editor UI can be checked via the user token
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        return ResponseEntity.ok().body(buildPlan);
    }

    @PutMapping("/programming-exercises/{exerciseId}/build-plan")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<BuildPlan> setBuildPlan(@PathVariable Long exerciseId, @RequestBody BuildPlan buildPlan) {
        log.debug("REST request to set build plan for programming exercise with id {}", exerciseId);

        final ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        buildPlanRepository.disconnectBuildPlanFromExercise(programmingExercise);
        buildPlan.addProgrammingExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        buildPlan = buildPlanRepository.save(buildPlan);

        return ResponseEntity.ok(buildPlan);
    }
}
