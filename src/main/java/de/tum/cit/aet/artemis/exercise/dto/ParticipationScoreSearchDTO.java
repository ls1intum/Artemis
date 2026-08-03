package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Search DTO for the exercise scores view, containing pagination, sorting, search term, filter, and score range parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationScoreSearchDTO(@Min(0) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProp,
        Integer scoreRangeLower, Integer scoreRangeUpper) {
}
