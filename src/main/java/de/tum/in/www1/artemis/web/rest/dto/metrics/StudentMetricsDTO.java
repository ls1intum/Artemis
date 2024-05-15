package de.tum.in.www1.artemis.web.rest.dto.metrics;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for student metrics.
 *
 * @param exerciseMetrics              the metrics for the exercises
 * @param lectureUnitStudentMetricsDTO the metrics for the lecture units
 * @param competencyMetrics            the metrics for the competencies
 */
public record StudentMetricsDTO(@NotNull ExerciseStudentMetricsDTO exerciseMetrics, @NotNull LectureUnitStudentMetricsDTO lectureUnitStudentMetricsDTO,
        @NotNull CompetencyStudentMetricsDTO competencyMetrics) {
}
