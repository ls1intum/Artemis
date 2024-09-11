package de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for the Iris competency generation feature.
 * A competency recommendation is just a title, description and taxonomy generated by Iris.
 *
 * @param title       The title of the competency
 * @param description The description of the competency
 * @param taxonomy    The taxonomy of the competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyRecommendationDTO(String title, String description, CompetencyTaxonomy taxonomy) {
}
