package de.tum.cit.aet.artemis.atlas.service.competency;

import java.util.List;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;

/**
 * Internal helper for competency imports that need the imported entity plus its outgoing relations.
 */
public record CompetencyWithTailRelation(CourseCompetency competency, List<CompetencyRelationDTO> tailRelations) {
}
