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

    private final AuthorizationCheckService authorizationCheckService;

    public BuildPlanResource(BuildPlanRepository buildPlanRepository, AuthorizationCheckService authorizationCheckService) {
        this.buildPlanRepository = buildPlanRepository;
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

        // authorization when called from the build plan editor UI can be checked via the user token,
        // if the endpoint was called from the continuous integration system, the secret is checked
        if (secret == null) {
            authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        }
        else if (!programmingExercise.hasBuildPlanAccessSecretSet() || !secret.equals(programmingExercise.getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }

        return ResponseEntity.ok().body(buildPlan.getBuildPlan());
    }

    @PutMapping("/programming-exercises/{exerciseId}/build-plan")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<BuildPlan> setBuildPlan(@PathVariable Long exerciseId, @RequestBody BuildPlan buildPlan) {
        log.debug("REST request to set build plan for programming exercise with id {}", exerciseId);

        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));

        // ToDo: fetch the exercise with exerciseId fresh from the database and check access there:
        // ToDo: the user might have edited the JSON for `buildPlan` manually and connected unrelated exercises

        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        // ToDo: a build plan might be connected to multiple exercises, but we only want to change it for `programmingExercise` here
        buildPlan = buildPlanRepository.save(buildPlan);

        return ResponseEntity.ok(buildPlan);
    }
}
