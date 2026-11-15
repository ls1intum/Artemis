package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO containing a {@link CourseCompetencyResponseDTO} and list of {@link CompetencyRelationDTO CompetencyRelation(DTO)s} for which it is the tail competency.
 *
 * @param competency    the competency
 * @param tailRelations relations where it is the tail competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyWithTailRelationDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) CourseCompetencyResponseDTO competency,
        List<CompetencyRelationDTO> tailRelations) {
}
