package de.tum.cit.aet.artemis.core.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Wrapper for a search result which is paged <br>
 *
 * @see org.springframework.data.domain.Pageable
 * @see SearchTermPageableSearchDTO
 * @param <T>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: convert to Record
public class SearchResultPageDTO<T> {

    /**
     * The search result
     */
    private List<T> resultsOnPage = new ArrayList<>();

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
