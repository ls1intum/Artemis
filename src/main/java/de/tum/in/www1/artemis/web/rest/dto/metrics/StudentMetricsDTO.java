package de.tum.in.www1.artemis.web.rest.dto.metrics;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for student metrics.
 *
 * @param exerciseMetrics   the metrics for the exercises
 * @param competencyMetrics the metrics for the competencies
 */
public record StudentMetricsDTO(@NotNull ExerciseStudentMetricsDTO exerciseMetrics, @NotNull CompetencyStudentMetricsDTO competencyMetrics) {
}
