package de.tum.cit.aet.artemis.athena.api;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Controller
public class AthenaScheduleAPi extends AbstractAthenaApi {

    private final Optional<AthenaScheduleService> optionalAthenaScheduleService;

    // ToDo: Replace with exercise API
    private final ExerciseRepository exerciseRepository;

    public AthenaScheduleAPi(Environment environment, Optional<AthenaScheduleService> optionalAthenaScheduleService, ExerciseRepository exerciseRepository) {
        super(environment);
        this.optionalAthenaScheduleService = optionalAthenaScheduleService;
        this.exerciseRepository = exerciseRepository;
    }

    public void scheduleExerciseForAthena(Long exerciseId) {
        if (optionalAthenaScheduleService.isPresent()) {
            // Athena does not work without exercises
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            optionalAthenaScheduleService.get().scheduleExerciseForAthenaIfRequired(exercise);
        }
    }

    public void cancelScheduledAthena(Long exerciseId) {
        optionalAthenaScheduleService.ifPresent(service -> service.cancelScheduledAthena(exerciseId));
    }
}
