package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;

/**
 * DTO containing {@link KnowledgeArea} data. It only contains the id of its parent.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaDTO(long id, String title, String description, Long parentId, List<KnowledgeAreaDTO> children, List<StandardizedCompetencyDTO> competencies) {
}
