package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardTimeSeriesEntryDTO(Instant bucketStart, Map<String, Double> series) {
}
