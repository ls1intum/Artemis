package de.tum.in.www1.artemis.web.rest.publicc;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.BUILD_PLAN;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Profile("gitlabci")
@RestController
@RequestMapping("public/")
public class BuildPlanResource {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BuildPlanRepository buildPlanRepository;

    public BuildPlanResource(BuildPlanRepository buildPlanRepository) {
        this.buildPlanRepository = buildPlanRepository;
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @param secret the secret to authenticate the request
     * @return the build plan stored in the database
     */
    @GetMapping(BUILD_PLAN)
    @EnforceNothing
    public ResponseEntity<String> getBuildPlan(@PathVariable Long exerciseId, @RequestParam("secret") String secret) {
        log.debug("REST request to get build plan for programming exercise with id {}", exerciseId);
        BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(exerciseId).orElseThrow();
        ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId).orElseThrow();
        if (!programmingExercise.hasBuildPlanAccessSecretSet() || !secret.equals(programmingExercise.getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }
        return ResponseEntity.ok().body(buildPlan.getBuildPlan());
    }
}
