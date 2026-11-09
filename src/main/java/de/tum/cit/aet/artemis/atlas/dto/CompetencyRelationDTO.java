package de.tum.cit.aet.artemis.atlas.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO containing {@link CompetencyRelation} data. It only contains ids of the linked competencies to reduce data sent.
 *
 * @param id               the id of the relation; omitted when creating one
 * @param tailCompetencyId the id of the tail competency
 * @param headCompetencyId the id of the head competency
 * @param relationType     the relation type
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyRelationDTO(@Schema(description = "The id of the relation. Omit it when creating a relation.") Long id,
        @Schema(description = "The id of the tail competency, the one that ASSUMES, EXTENDS or MATCHES the head. Required.", requiredMode = Schema.RequiredMode.REQUIRED) long tailCompetencyId,
        @Schema(description = "The id of the head competency. Required. Both competencies must belong to the same course.", requiredMode = Schema.RequiredMode.REQUIRED) long headCompetencyId,
        @Schema(description = """
                ASSUMES: the tail assumes the student already achieved the head. EXTENDS: the tail covers the head's topic in more \
                detail. MATCHES: the tail matches the head, for example a duplicate.""", requiredMode = Schema.RequiredMode.REQUIRED) RelationType relationType)
        implements Serializable {

    public static CompetencyRelationDTO of(CompetencyRelation competencyRelation) {
        return new CompetencyRelationDTO(competencyRelation.getId(), competencyRelation.getTailCompetency().getId(), competencyRelation.getHeadCompetency().getId(),
                competencyRelation.getType());
    }
}
