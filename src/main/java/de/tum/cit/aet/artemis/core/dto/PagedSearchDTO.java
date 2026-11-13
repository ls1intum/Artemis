package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pagination, search term, sort info and an optional filter for paged list endpoints.
 * <p>
 * This is a dedicated, constrained counterpart to {@link de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO}:
 * that class is shared by many endpoints, is unconstrained, and treats {@code page} as 1-based in some call sites
 * (via {@code PageUtil.createDefaultPageRequest}) and 0-based in others, so constraints cannot be added to it directly.
 * Bounding {@code page} and {@code pageSize} here keeps an omitted, negative, or unbounded value from reaching
 * {@link org.springframework.data.domain.PageRequest#of} (which throws, producing a 500) or loading the whole list.
 *
 * @param page         zero-based page index, at most 100,000 (keeps {@code page * pageSize} far below the {@code int} range the JPA pipeline narrows the offset to)
 * @param pageSize     number of results per page (1-200)
 * @param sortingOrder ascending or descending
 * @param sortedColumn the column to sort by
 * @param searchTerm   the text to filter by
 * @param filterProp   an optional endpoint-specific filter value, e.g. {@code "Submitted"}; ignored by endpoints without filters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PagedSearchDTO(@Min(0) @Max(100_000) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm,
        @Nullable String filterProp) {
}
