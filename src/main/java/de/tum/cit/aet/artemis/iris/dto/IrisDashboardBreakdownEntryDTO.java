package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardBreakdownEntryDTO(String label, Map<String, Double> values) {
}
