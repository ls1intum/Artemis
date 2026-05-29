package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGenerationRequestDTO(@NotBlank String courseDescription, @Nullable List<@Valid CompetencyRecommendationDTO> currentCompetencies) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyRecommendationDTO(String title, String description, CompetencyTaxonomy taxonomy) {
    }
}
