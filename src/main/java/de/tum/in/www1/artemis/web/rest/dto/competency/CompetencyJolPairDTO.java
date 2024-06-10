package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;

/**
 * This DTO is used to represent a pair of CompetencyJolDTOs, where the first one is the current judgement of learning value and the second one is the judgement of learning value
 * prior to the most recent one.
 *
 * @param current the current judgement of learning value
 * @param prior   the judgement of learning value prior to the most recent one
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyJolPairDTO(CompetencyJolDTO current, CompetencyJolDTO prior) {

    public static CompetencyJolPairDTO of(CompetencyJol current, CompetencyJol prior) {
        return new CompetencyJolPairDTO(current != null ? CompetencyJolDTO.of(current) : null, prior != null ? CompetencyJolDTO.of(prior) : null);
    }
}
