package de.tum.cit.aet.artemis.web.rest.dto.competency;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathCompetencyGraphDTO(Set<CompetencyGraphNodeDTO> nodes, Set<CompetencyGraphEdgeDTO> edges) {
}
