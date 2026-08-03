package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Search DTO for the Iris assessment review overview, containing pagination, sorting, search term, and verdict filters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentReviewSearchDTO(int page, int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm, String filterProps) {
}
