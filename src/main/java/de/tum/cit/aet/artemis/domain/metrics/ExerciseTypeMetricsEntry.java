package de.tum.cit.aet.artemis.domain.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;

/**
 * Custom record for SQL queries.
 * Can be used in a Map-like way:
 * There is at most one entry for each ExerciseType, together with the corresponding value.
 *
 * @param exerciseType an ExerciseType
 * @param value        the value corresponding to the exerciseType
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeMetricsEntry(Class<? extends Exercise> exerciseType, long value) {
}
