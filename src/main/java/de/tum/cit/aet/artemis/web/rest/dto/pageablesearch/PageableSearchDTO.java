package de.tum.cit.aet.artemis.web.rest.dto.pageablesearch;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;

/**
 * Wrapper for a generic search for any list of entities. The result should be paged,
 * meaning that it only contains a predefined number of elements in order to not fetch and return too many.
 *
 * @see SearchResultPageDTO
 */
// TODO: convert to Record
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PageableSearchDTO<T> {

    /**
     * The number of the page to return
     */
    private int page;

    /**
     * The maximum size of one page
     */
    private int pageSize;

    /**
     * The sort order, i.e. descending or ascending
     */
    private SortingOrder sortingOrder;

    /**
     * The column for which the result should be sorted by
     */
    private T sortedColumn;

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
