package de.tum.cit.aet.artemis.metis.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The results dashboard of an exercise: the summary tiles and one row per comprehension check. The row list is always sent, also when it is empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisDashboardDTO(@NotNull MetisDashboardSummaryDTO summary, @JsonInclude @NotNull List<MetisDashboardRowDTO> rows) {
}
