package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;

/**
 * DTO containing {@link StandardizedCompetency} data. It only contains the id of the knowledge area, source.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, String version, Long knowledgeAreaId, Long sourceId) {

}
