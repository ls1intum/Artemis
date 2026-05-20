package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

public record IrisDashboardBreakdownEntryDTO(String name, Map<String, Double> metrics) {
}
