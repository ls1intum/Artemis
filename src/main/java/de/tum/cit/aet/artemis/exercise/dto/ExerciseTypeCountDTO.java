package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Objects;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

public record ExerciseTypeCountDTO(ExerciseType exerciseType, long count) {

    public ExerciseTypeCountDTO(Class<?> exerciseType, Long count) {
        this(ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class)), Objects.requireNonNullElse(count, 0L));
    }
}
