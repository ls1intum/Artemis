package de.tum.cit.aet.artemis.athena.api;

import java.util.Optional;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Controller
public class AthenaScheduleAPi {

    private final Optional<AthenaScheduleService> optionalAthenaScheduleService;

    // ToDo: Replace with exercise API
    private final ExerciseRepository exerciseRepository;

    public AthenaScheduleAPi(Optional<AthenaScheduleService> optionalAthenaScheduleService, ExerciseRepository exerciseRepository) {
        this.optionalAthenaScheduleService = optionalAthenaScheduleService;
        this.exerciseRepository = exerciseRepository;
    }

    public void scheduleExerciseForAthena(Long exerciseId) {
        if (optionalAthenaScheduleService.isPresent()) {
            // Athena does not work without exercises
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            optionalAthenaScheduleService.ifPresent(service -> service.scheduleExerciseForAthenaIfRequired(exercise));
        }
    }

    public void cancelScheduledAthena(Long exerciseId) {
        optionalAthenaScheduleService.ifPresent(service -> service.cancelScheduledAthena(exerciseId));
    }
}
