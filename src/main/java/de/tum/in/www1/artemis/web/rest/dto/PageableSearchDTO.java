package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;

public class PageableSearchDTO<T> {

    private int page;

    private int pageSize;

    private String searchTerm;

    private SortingOrder sortingOrder;

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
