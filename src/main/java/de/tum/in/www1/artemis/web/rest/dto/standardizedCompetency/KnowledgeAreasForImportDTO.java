package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Source;

/**
 * DTO including a nested structure of knowledgeAreaDTOs (including their competencies), as well as a list of sources
 * This is used to import new knowledge areas, standardized competencies and sources into Artemis
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreasForImportDTO(List<KnowledgeAreaDTO> knowledgeAreas, List<Source> sources) {
}
