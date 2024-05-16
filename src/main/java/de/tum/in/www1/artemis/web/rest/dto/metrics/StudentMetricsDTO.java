package de.tum.in.www1.artemis.web.rest.dto.metrics;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for student metrics.
 * <p>
 * Note: When updating this class, make sure to also update Pyris.
 *
 * @param exerciseMetrics the metrics for the exercises
 */
public record StudentMetricsDTO(@NotNull ExerciseStudentMetricsDTO exerciseMetrics) {
}
