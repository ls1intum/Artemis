package de.tum.in.www1.artemis.service.connectors.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

@JsonInclude
public record PyrisCompetencyRecommendationDTO(String title, String description, CompetencyTaxonomy taxonomy) {
}
