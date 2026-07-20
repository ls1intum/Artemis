package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ordered generated assessment criteria.
 */
@Schema(description = "Generated structured assessment criteria")
public record AssessmentCriteriaGenerationResponseDTO(@NotEmpty @Schema(description = "Ordered generated criteria") List<@Valid GeneratedAssessmentCriterionDTO> criteria) {
}
