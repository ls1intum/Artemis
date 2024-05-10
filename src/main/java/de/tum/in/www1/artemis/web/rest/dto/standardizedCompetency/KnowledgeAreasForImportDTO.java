package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;

/**
 * DTO including a nested structure of knowledge areas (including their descendants), as well as a list of sources
 * This is used to import new knowledge areas, standardized competencies and sources into Artemis
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreasForImportDTO(List<@Valid KnowledgeAreaForImportDTO> knowledgeAreas, List<Source> sources) {

    /**
     * DTO containing knowledge area data as well as its children and competencies
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record KnowledgeAreaForImportDTO(@NotNull @Size(min = 1, max = KnowledgeArea.MAX_TITLE_LENGTH) String title,
            @NotNull @Size(min = 1, max = KnowledgeArea.MAX_SHORT_TITLE_LENGTH) String shortTitle, @Size(max = KnowledgeArea.MAX_DESCRIPTION_LENGTH) String description,
            List<@Valid KnowledgeAreaForImportDTO> children, List<@Valid StandardizedCompetencyForImportDTO> competencies) {

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StandardizedCompetencyForImportDTO(@NotNull @Size(min = 1, max = StandardizedCompetency.MAX_TITLE_LENGTH) String title,
            @Size(max = StandardizedCompetency.MAX_DESCRIPTION_LENGTH) String description, CompetencyTaxonomy taxonomy,
            @Size(min = 1, max = StandardizedCompetency.MAX_VERSION_LENGTH) String version, Long sourceId) {
    }
}
