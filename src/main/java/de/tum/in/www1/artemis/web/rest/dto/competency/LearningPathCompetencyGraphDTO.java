package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Set;

public record LearningPathCompetencyGraphDTO(Set<CompetencyProgressDTO> competencies, Set<CompetencyRelationDTO> competencyRelations) {
}
