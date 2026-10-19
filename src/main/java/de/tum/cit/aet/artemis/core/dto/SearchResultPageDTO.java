package de.tum.cit.aet.artemis.core.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    /**
     * Convenience constructor for building a page in application code.
     * <p>
     * Explicitly not a Jackson creator. Artemis compiles with {@code -parameters}, and Jackson 3 turns a lone
     * argument-taking constructor into an implicit properties-based creator when it can read the parameter names —
     * so a response that omits {@code resultsOnPage} (which {@code NON_EMPTY} does whenever a search finds nothing)
     * would bind it to {@code null} instead of leaving the empty list this class initialises. Jackson 2 used the
     * no-argument constructor and the setters, which is the behaviour {@code Mode.DISABLED} restores.
     *
     * @param resultsOnPage the search results on this page
     * @param numberOfPages the total number of available pages for the search
     */
    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
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
