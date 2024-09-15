package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

/**
 * DTO containing {@link CompetencyRelation} data. It only contains ids of the linked competencies to reduce data sent.
 *
 * @param tailCompetencyId the id of the tail competency
 * @param headCompetencyId the id of the head competency
 * @param relationType     the relation type
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyRelationDTO(Long id, long tailCompetencyId, long headCompetencyId, RelationType relationType) {

    public static CompetencyRelationDTO of(CompetencyRelation competencyRelation) {
        return new CompetencyRelationDTO(competencyRelation.getId(), competencyRelation.getTailCompetency().getId(), competencyRelation.getHeadCompetency().getId(),
                competencyRelation.getType());
    }
}
