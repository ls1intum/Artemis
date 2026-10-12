package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Pagination, search term, sort info and an optional filter for the paged exam-students endpoint.
 *
 * @param page         zero-based page index, at most 100,000 (keeps {@code page * pageSize} far below the {@code int} range the JPA pipeline narrows the offset to)
 * @param pageSize     number of results per page (1-200)
 * @param sortingOrder ascending or descending
 * @param sortedColumn the column to sort by
 * @param searchTerm   the text to filter students by
 * @param filterProp   an optional filter value, e.g. {@code "Submitted"} or {@code "AttendanceNotChecked"}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamStudentSearchDTO(@Min(0) @Max(100_000) int page, @Min(1) @Max(200) int pageSize, SortingOrder sortingOrder, String sortedColumn, String searchTerm,
        String filterProp) {
}
