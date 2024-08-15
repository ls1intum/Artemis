package de.tum.in.www1.artemis.web.rest.dto.metrics;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for student metrics.
 * <p>
 * Note: When updating this class, make sure to also update Pyris.
 *
 * @param exerciseMetrics              the metrics for the exercises
 * @param lectureUnitStudentMetricsDTO the metrics for the lecture units
 * @param competencyMetrics            the metrics for the competencies
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentMetricsDTO(@NonNull ExerciseStudentMetricsDTO exerciseMetrics, @NonNull LectureUnitStudentMetricsDTO lectureUnitStudentMetricsDTO,
        @NonNull CompetencyStudentMetricsDTO competencyMetrics) {
}
