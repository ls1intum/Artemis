package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCompetencyRecommendationDTO(String title, String description, CompetencyTaxonomy taxonomy) {
}
