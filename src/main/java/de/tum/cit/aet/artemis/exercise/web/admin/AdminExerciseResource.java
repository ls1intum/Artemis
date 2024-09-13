package de.tum.cit.aet.artemis.exercise.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * REST controller for administrating Exercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExerciseResource.class);

    private final ExerciseRepository exerciseRepository;

    public AdminExerciseResource(ExerciseRepository exerciseRepository) {
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
