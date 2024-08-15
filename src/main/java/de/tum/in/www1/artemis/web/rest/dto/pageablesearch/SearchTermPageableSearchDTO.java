package de.tum.in.www1.artemis.web.rest.dto.pageablesearch;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

/**
 * Wrapper for a generic search for any list of entities matching a given search term. The result should be paged,
 * meaning that it only contains a predefined number of elements in order to not fetch and return too many.
 *
 * @param <T> The type of the column for which the result should be sorted by
 * @see SearchResultPageDTO
 */
// TODO: convert to Record, use composition for common attributes
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SearchTermPageableSearchDTO<T> extends PageableSearchDTO<T> {

    /**
     * The string to search for
     */
    protected String searchTerm;

    // make sure to avoid null values and instead return an empty string
    @NonNull
    public String getSearchTerm() {
        return searchTerm != null ? searchTerm : "";
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }
}
