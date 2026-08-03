package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Search DTO for the participation management view, containing pagination, sorting, search term, and filter parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationSearchDTO(@Min(0) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp) {
}
