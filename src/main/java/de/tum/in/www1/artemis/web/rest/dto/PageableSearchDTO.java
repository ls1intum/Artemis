package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.BasePageableSearchDTO;

/**
 * Wrapper for a generic search for any list of entities matching a given search term. The result should be paged,
 * meaning that it only contains a predefined number of elements in order to not fetch and return too many.
 *
 * @param <T> The type of the column for which the result should be sorted by
 * @see SearchResultPageDTO
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PageableSearchDTO<T> extends BasePageableSearchDTO<T> {

    /**
     * The string to search for
     */
    protected String searchTerm;

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }
}
