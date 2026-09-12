package de.tum.cit.aet.artemis.exercise.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.exercise.dto.UpcomingExerciseDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * REST controller for administrating Exercise.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("management/exercise-management")
@RestController
@RequestMapping("api/exercise/admin/")
public class AdminExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExerciseResource.class);

    private final ExerciseRepository exerciseRepository;

    public AdminExerciseResource(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * GET /exercises/upcoming : Find all exercises that have an upcoming due date.
     *
     * @return the ResponseEntity with status 200 (OK) and the upcoming exercises, ordered by due date.
     */
    @GetMapping("exercises/upcoming")
    public ResponseEntity<List<UpcomingExerciseDTO>> getUpcomingExercises() {
        log.debug("REST request to get all upcoming exercises");
        List<UpcomingExerciseDTO> upcomingExercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDate().stream()
                .sorted(Comparator.comparing(exercise -> exercise.getDueDate(), Comparator.nullsLast(Comparator.naturalOrder()))).map(UpcomingExerciseDTO::of).toList();
        return ResponseEntity.ok(upcomingExercises);
    }
}
