package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;

/**
 * DTO including a nested structure of knowledgeAreaDTOs (including their competencies), as well as a list of sources
 * This is used to import new knowledge areas, standardized competencies and sources into Artemis
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreasForImportDTO(List<@Valid KnowledgeAreaDTOWithDescendants> knowledgeAreas, List<Source> sources) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record KnowledgeAreaDTOWithDescendants(@NotNull @Size(min = 1, max = KnowledgeArea.MAX_TITLE_LENGTH) String title,
            @NotNull @Size(min = 1, max = KnowledgeArea.MAX_SHORT_TITLE_LENGTH) String shortTitle, @Size(max = KnowledgeArea.MAX_DESCRIPTION_LENGTH) String description,
            Long parentId, List<@Valid KnowledgeAreaDTOWithDescendants> children, List<@Valid StandardizedCompetencyRequestDTO> competencies) {

    }
}
