package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;

/**
 * DTO containing a {@link Competency} and list of {@link CompetencyRelationDTO CompetencyRelation(DTO)s} for which it is the tail competency.
 *
 * @param competency    the competency
 * @param tailRelations relations where it is the tail competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyWithTailRelationDTO(CourseCompetency competency, List<CompetencyRelationDTO> tailRelations) {

}
