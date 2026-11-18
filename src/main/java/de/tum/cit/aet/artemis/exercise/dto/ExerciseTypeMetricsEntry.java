package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Custom record for SQL queries.
 * Can be used in a Map-like way:
 * There is at most one entry for each ExerciseType, together with the corresponding value.
 *
 * @param exerciseType an {@link ExerciseType}
 * @param value        the value corresponding to the exerciseType
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeMetricsEntry(ExerciseType exerciseType, long value) {

    /**
     * JPQL constructor that accepts the raw entity class produced by Hibernate's {@code TYPE(...)} function
     * and maps it to the {@link ExerciseType} discriminator used by the canonical record component.
     */
    public ExerciseTypeMetricsEntry(Class<? extends Exercise> exerciseType, long value) {
        this(ExerciseType.getExerciseTypeFromClass(exerciseType), value);
    }
}
