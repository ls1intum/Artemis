package de.tum.in.www1.artemis.domain.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeMetricsEntry(Class<? extends Exercise> exerciseType, long value) {
}
