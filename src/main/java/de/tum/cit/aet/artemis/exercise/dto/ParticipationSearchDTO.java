package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Search DTO for the participation management view, containing pagination, sorting, search term, and filter parameters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationSearchDTO(int page, int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp) {
}
