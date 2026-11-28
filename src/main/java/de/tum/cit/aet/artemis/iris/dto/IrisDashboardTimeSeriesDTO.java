package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ordered metric buckets for a valid dashboard window, including buckets without activity.
 *
 * @param metric  metric whose series is returned
 * @param entries nonempty ordered buckets covering the requested window
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardTimeSeriesDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) IrisDashboardMetric metric,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IrisDashboardTimeSeriesEntryDTO> entries) {
}
