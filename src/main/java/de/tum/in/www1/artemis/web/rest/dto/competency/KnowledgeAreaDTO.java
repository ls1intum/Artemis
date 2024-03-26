package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaDTO(String title, String description, Long parentId, Set<KnowledgeAreaDTO> children, Set<StandardizedCompetencyDTO> competencies) {
}
