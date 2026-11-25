package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricScale;

/**
 * A criterion of a presentation rubric. Its position is its index in the rubric.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricCriterionDTO(Long id, @NotBlank @Size(max = PresentationRubricCriterion.MAX_NAME_LENGTH) String name,
        @Size(max = PresentationRubricCriterion.MAX_DESCRIPTION_LENGTH) String description, @NotNull @Min(1) @Max(100) Integer weight, @NotNull PresentationRubricScale scale,
        @NotNull List<@NotNull @Valid PresentationRubricLevelDTO> levels) {
}
