package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A dimension label and its complete metric map.
 *
 * @param label  dimension label, omitted when empty
 * @param values nonempty numeric metrics for the requested dimension
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardBreakdownEntryDTO(String label, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Double> values) {
}
