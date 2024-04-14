package de.tum.in.www1.artemis.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.KnowledgeAreaDTO;

/**
 * Test DTO for {@link KnowledgeAreaDTO} where we can deserialize the ids
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAreaTestDTO(Long id, String title, String shortTitle, String description, Long parentId, List<KnowledgeAreaTestDTO> children,
        List<StandardizedCompetencyTestDTO> competencies) {
}
