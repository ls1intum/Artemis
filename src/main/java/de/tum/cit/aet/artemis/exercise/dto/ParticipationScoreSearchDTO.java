package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Search DTO for the exercise scores view, containing pagination, sorting, search term, filter, and score range parameters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipationScoreSearchDTO(int page, int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp, Integer scoreRangeLower,
        Integer scoreRangeUpper) {
}
