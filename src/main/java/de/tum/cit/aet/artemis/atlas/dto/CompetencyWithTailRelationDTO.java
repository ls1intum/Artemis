package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing a {@link CourseCompetencyResponseDTO} and list of {@link CompetencyRelationDTO CompetencyRelation(DTO)s} for which it is the tail competency.
 *
 * @param competency    the competency
 * @param tailRelations relations where it is the tail competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyWithTailRelationDTO(CourseCompetencyResponseDTO competency, List<CompetencyRelationDTO> tailRelations) {
}
