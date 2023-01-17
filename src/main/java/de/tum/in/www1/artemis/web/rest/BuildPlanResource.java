package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation.log;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.BUILD_PLAN;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;

@RestController
@RequestMapping(ROOT)
public class BuildPlanResource {

    private final BuildPlanRepository buildPlanRepository;
    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public BuildPlanResource(BuildPlanRepository buildPlanRepository,
                             ProgrammingExerciseRepository programmingExerciseRepository) {
        this.buildPlanRepository = buildPlanRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @param secret the secret to authenticate the request
     * @return the build plan stored in the database
     */
    @GetMapping(BUILD_PLAN)
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> getBuildPlan(@PathVariable Long exerciseId, @RequestParam("secret") String secret) {
        log.debug("REST request to get build plan for programming exercise with id : {}", exerciseId);
        Optional<ProgrammingExercise> optionalProgrammingExercise = programmingExerciseRepository.findById(exerciseId);
        if (optionalProgrammingExercise.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProgrammingExercise programmingExercise = optionalProgrammingExercise.get();
        if (programmingExercise.getBuildPlanAccessSecret() == null || !secret.equals(programmingExercise.getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }
        return ResponseEntity.ok().body(programmingExercise.getBuildPlan().getBuildPlan());
    }
}
