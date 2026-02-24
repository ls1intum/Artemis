package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyRequestDTO(Long id, @NotBlank @Size(max = 255) String title, @Size(max = 10000) String description, ZonedDateTime softDueDate,
        @NotNull @Min(1) @Max(100) Integer masteryThreshold, CompetencyTaxonomy taxonomy, Boolean optional) {
}
