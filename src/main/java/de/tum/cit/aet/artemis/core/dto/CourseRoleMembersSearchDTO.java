package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pagination, search term, and sort info for the paged course-role membership endpoint.
 * <p>
 * This is a dedicated, constrained counterpart to {@link de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO}:
 * that class is shared by many endpoints, is unconstrained, and treats {@code page} as 1-based in some call sites
 * (via {@code PageUtil.createDefaultPageRequest}) and 0-based in others, so constraints cannot be added to it directly.
 * Bounding {@code page} and {@code pageSize} here keeps an omitted, negative, or unbounded value from reaching
 * {@link org.springframework.data.domain.PageRequest#of} (which throws, producing a 500) or loading the whole membership.
 * Mirrors {@code ExamStudentSearchDTO} for the analogous exam-members endpoint.
 *
 * @param page         zero-based page index
 * @param pageSize     number of results per page (1-200)
 * @param sortingOrder ascending or descending
 * @param sortedColumn the column to sort by
 * @param searchTerm   the text to filter members by (login or full name)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRoleMembersSearchDTO(@Min(0) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm) {
}
