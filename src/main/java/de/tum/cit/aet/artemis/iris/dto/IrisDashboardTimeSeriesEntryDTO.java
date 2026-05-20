package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.Map;

public record IrisDashboardTimeSeriesEntryDTO(Instant bucketStart, Map<String, Double> series) {
}
