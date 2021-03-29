package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper for a search result which is paged <br>
 *
 * @see org.springframework.data.domain.Pageable
 * @see PageableSearchDTO
 * @param <T>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SearchResultPageDTO<T> {

    /**
     * The search result
     */
    private List<T> resultsOnPage;

    /**
     * The total number of available pages for the given search
     */
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
