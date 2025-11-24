package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

/**
 * DTO for previewing a competency relation before creation.
 * Contains information about the head and tail competencies and the relation type.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyRelationPreviewDTO(@Nullable Long relationId, long headCompetencyId, String headCompetencyTitle, long tailCompetencyId, String tailCompetencyTitle,
        RelationType relationType) {
}
