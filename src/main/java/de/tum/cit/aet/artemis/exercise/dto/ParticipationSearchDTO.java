package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Search DTO for the participation management view, extending the standard pageable search with
 * a filter parameter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ParticipationSearchDTO extends SearchTermPageableSearchDTO<String> {

    private String filterProp = "All";

    public String getFilterProp() {
        return filterProp;
    }

    public void setFilterProp(String filterProp) {
        this.filterProp = filterProp;
    }
}
