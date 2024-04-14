package de.tum.in.www1.artemis.competency;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyDTO;

/**
 * Test DTO for {@link StandardizedCompetencyDTO} where we can deserialize the id
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandardizedCompetencyTestDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy, String version, Long knowledgeAreaId, Long sourceId) {
}
