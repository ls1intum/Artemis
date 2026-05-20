package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

public record IrisDashboardTimeSeriesDTO(IrisDashboardMetric metric, List<IrisDashboardTimeSeriesEntryDTO> entries) {
}
