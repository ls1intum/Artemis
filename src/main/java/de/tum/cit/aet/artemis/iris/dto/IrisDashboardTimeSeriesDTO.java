package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardTimeSeriesDTO(IrisDashboardMetric metric, List<IrisDashboardTimeSeriesEntryDTO> entries) {
}
