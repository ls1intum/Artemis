package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;

/**
 * A DTO for the CompetencyJol entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyJolDTO(long competencyId, short jolValue, ZonedDateTime judgementTime, Double competencyProgress, Double competencyConfidence) {

    public static CompetencyJolDTO of(CompetencyJol competencyJol) {
        return new CompetencyJolDTO(competencyJol.getCompetency().getId(), competencyJol.getValue(), competencyJol.getJudgementTime(), competencyJol.getCompetencyProgress(),
                competencyJol.getCompetencyConfidence());
    }
}
