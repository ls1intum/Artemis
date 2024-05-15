package de.tum.in.www1.artemis.web.rest.dto.metrics;

import jakarta.validation.constraints.NotNull;

public record StudentMetricsDTO(@NotNull ExerciseStudentMetricsDTO exerciseMetrics) {
}
