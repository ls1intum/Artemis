package de.tum.cit.aet.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;

/**
 * A DTO for the CompetencyJol entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyJolDTO(long id, long competencyId, short jolValue, ZonedDateTime judgementTime, double competencyProgress, double competencyConfidence) {

    public static CompetencyJolDTO of(@NotNull CompetencyJol competencyJol) {
        return new CompetencyJolDTO(competencyJol.getId(), competencyJol.getCompetency().getId(), competencyJol.getValue(), competencyJol.getJudgementTime(),
                competencyJol.getCompetencyProgress(), competencyJol.getCompetencyConfidence());
    }
}
