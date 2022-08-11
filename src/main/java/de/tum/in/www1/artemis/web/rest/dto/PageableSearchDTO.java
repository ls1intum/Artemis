package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;

/**
 * Wrapper for a generic search for any list of entities matching a given search term. The result should be paged,
 * meaning that it only contains a predefined number of elements in order to not fetch and return too many.
 *
 * @see SearchResultPageDTO
 * @param <T> The type of the column for which the result should be sorted by
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PageableSearchDTO<T> {

    /**
     * The number of the page to return
     */
    protected int page;

    /**
     * The maximum size of one page
     */
    protected int pageSize;

    /**
     * The string to search for
     */
    protected String searchTerm;

    /**
     * The sort order, i.e. descending or ascending
     */
    protected SortingOrder sortingOrder;

    /**
     * The column for which the result should be sorted by
     */
    protected T sortedColumn;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public SortingOrder getSortingOrder() {
        return sortingOrder;
    }

    public void setSortingOrder(SortingOrder sortingOrder) {
        this.sortingOrder = sortingOrder;
    }

    public T getSortedColumn() {
        return sortedColumn;
    }

    public void setSortedColumn(T sortedColumn) {
        this.sortedColumn = sortedColumn;
    }
}
