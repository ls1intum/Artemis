package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Search DTO for the exercise scores view, extending the standard pageable search with
 * filter and score range parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ParticipationScoreSearchDTO extends SearchTermPageableSearchDTO<String> {

    private String filterProp = "All";

    private Integer scoreRangeLower;

    private Integer scoreRangeUpper;

    public String getFilterProp() {
        return filterProp;
    }

    public void setFilterProp(String filterProp) {
        this.filterProp = filterProp;
    }

    public Integer getScoreRangeLower() {
        return scoreRangeLower;
    }

    public void setScoreRangeLower(Integer scoreRangeLower) {
        this.scoreRangeLower = scoreRangeLower;
    }

    public Integer getScoreRangeUpper() {
        return scoreRangeUpper;
    }

    public void setScoreRangeUpper(Integer scoreRangeUpper) {
        this.scoreRangeUpper = scoreRangeUpper;
    }
}
