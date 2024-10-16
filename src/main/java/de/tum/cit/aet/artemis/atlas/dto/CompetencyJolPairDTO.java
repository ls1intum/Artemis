package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;

/**
 * This DTO is used to represent a pair of CompetencyJolDTOs, where the first one is the current judgement of learning value and the second one is the judgement of learning value
 * prior to the most recent one.
 *
 * @param current the current judgement of learning value
 * @param prior   the judgement of learning value prior to the most recent one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyJolPairDTO(CompetencyJolDTO current, CompetencyJolDTO prior) {

    public static CompetencyJolPairDTO from(CompetencyJol current, CompetencyJol prior) {
        return new CompetencyJolPairDTO(current != null ? CompetencyJolDTO.from(current) : null, prior != null ? CompetencyJolDTO.from(prior) : null);
    }
}
