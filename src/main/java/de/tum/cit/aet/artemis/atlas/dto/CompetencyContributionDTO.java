package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyContributionDTO(long competencyId, @NotEmpty String title, double weight, double mastery) {
}
