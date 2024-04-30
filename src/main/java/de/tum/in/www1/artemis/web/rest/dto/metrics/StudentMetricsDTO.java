package de.tum.in.www1.artemis.web.rest.dto.metrics;

import javax.validation.constraints.NotNull;

public record StudentMetricsDTO(@NotNull ExerciseStudentMetricsDTO exerciseMetrics, @NotNull LectureUnitStudentMetricsDTO lectureUnitMetrics) {
}
