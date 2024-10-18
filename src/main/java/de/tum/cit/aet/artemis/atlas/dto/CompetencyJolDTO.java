package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;

/**
 * Pyris DTO mapping for a {@code CompetencyJol}.
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyJolDTO(
        long id,
        long competencyId,
        short jolValue,
        ZonedDateTime judgementTime,
        double competencyProgress,
        double competencyConfidence
// @formatter:on
) {

    public static CompetencyJolDTO from(@NotNull CompetencyJol competencyJol) {
        // @formatter:off
        return new CompetencyJolDTO(
                competencyJol.getId(),
                competencyJol.getCompetency().getId(),
                competencyJol.getValue(),
                competencyJol.getJudgementTime(),
                competencyJol.getCompetencyProgress(),
                competencyJol.getCompetencyConfidence()
        );
        // @formatter:on
    }
}
