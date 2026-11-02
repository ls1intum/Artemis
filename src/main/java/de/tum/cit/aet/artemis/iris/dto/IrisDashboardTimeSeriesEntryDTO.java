package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A UTC bucket and its complete metric series, including zero values.
 *
 * @param bucketStart UTC bucket start instant
 * @param series      nonempty map of metric-specific numeric series
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardTimeSeriesEntryDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant bucketStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Double> series) {
}
