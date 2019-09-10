package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class SearchResultPageDTO<T> {

    private List<T> resultsOnPage;

    private int numberOfPages;

    public SearchResultPageDTO() {
    }

    public SearchResultPageDTO(List<T> resultsOnPage, int numberOfPages) {
        this.resultsOnPage = resultsOnPage;
        this.numberOfPages = numberOfPages;
    }

    public List<T> getResultsOnPage() {
        return resultsOnPage;
    }

    public void setResultsOnPage(List<T> resultsOnPage) {
        this.resultsOnPage = resultsOnPage;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }
}
