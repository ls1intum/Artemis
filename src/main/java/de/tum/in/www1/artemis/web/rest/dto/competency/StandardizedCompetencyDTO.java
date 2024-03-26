package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyDTO(String title, String description, CompetencyTaxonomy taxonomy, String version, Long knowledgeAreaId, Long sourceId) {

}
