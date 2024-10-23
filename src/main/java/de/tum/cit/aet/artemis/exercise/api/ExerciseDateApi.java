package de.tum.cit.aet.artemis.exercise.api;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;

@Controller
public class ExerciseDateApi extends AbstractExerciseApi {

    private final Optional<ExerciseDateService> exerciseDateService;

    public ExerciseDateApi(Environment environment, Optional<ExerciseDateService> exerciseDateService) {
        super(environment);
        this.exerciseDateService = exerciseDateService;
    }

    public boolean isAfterLatestDueDate(Exercise exercise) {
        return getOrThrow(exerciseDateService).isAfterLatestDueDate(exercise);
    }

    public boolean isAfterAssessmentDueDate(Exercise exercise) {
        return ExerciseDateService.isAfterAssessmentDueDate(exercise);
    }

    public Optional<ZonedDateTime> getDueDate(ParticipationInterface participation) {
        return ExerciseDateService.getDueDate(participation);
    }

    public boolean isBeforeLatestDueDate(Exercise exercise) {
        return getOrThrow(exerciseDateService).isBeforeLatestDueDate(exercise);
    }

    public boolean isBeforeDueDate(ParticipationInterface participation) {
        return getOrThrow(exerciseDateService).isBeforeDueDate(participation);
    }

    public boolean isAfterDueDate(ParticipationInterface participation) {
        return getOrThrow(exerciseDateService).isAfterDueDate(participation);
    }

    @Nullable
    public ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        return getOrThrow(exerciseDateService).getIndividualDueDate(exercise, participation);
    }

    public Optional<Boolean> isBeforeEarliestDueDate(Exercise exercise) {
        return getOrThrow(exerciseDateService).isBeforeEarliestDueDate(exercise);
    }

    public boolean hasExerciseStarted(Exercise exercise) {
        return getOrThrow(exerciseDateService).hasExerciseStarted(exercise);
    }
}
