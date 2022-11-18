package de.tum.in.www1.artemis.web.rest.admin;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for administrating Exercise.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminExerciseResource {

    private final Logger log = LoggerFactory.getLogger(AdminExerciseResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    public AdminExerciseResource(AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository) {
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET /exercises/upcoming : Find all exercises that have an upcoming due date.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exercises.
     */
    @GetMapping("exercises/upcoming")
    @EnforceAdmin
    public ResponseEntity<Set<Exercise>> getUpcomingExercises() {
        log.debug("REST request to get all upcoming exercises");
        Set<Exercise> upcomingExercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDate();
        return ResponseEntity.ok(upcomingExercises);
    }
}
