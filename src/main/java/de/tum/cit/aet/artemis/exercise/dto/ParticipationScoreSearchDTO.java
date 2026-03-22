package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Search DTO for the exercise scores view, extending the participation search with
 * score range parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ParticipationScoreSearchDTO extends ParticipationSearchDTO {

    private Integer scoreRangeLower;

    private Integer scoreRangeUpper;

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
