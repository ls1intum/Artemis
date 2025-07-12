package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeCountDTO(ExerciseType exerciseType, long count) {

    public ExerciseTypeCountDTO(Class<?> exerciseType, Long count) {
        this(ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class)), Objects.requireNonNullElse(count, 0L));
    }
}
