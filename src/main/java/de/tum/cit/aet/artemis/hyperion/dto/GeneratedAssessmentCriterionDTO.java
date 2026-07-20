package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One generated structured assessment criterion.
 */
@Schema(description = "Generated structured assessment criterion")
public record GeneratedAssessmentCriterionDTO(@NotBlank @Size(max = 255) @Schema(description = "Criterion title") String title,
        @NotEmpty @Schema(description = "Ordered grading instructions") List<@Valid GeneratedAssessmentInstructionDTO> structuredGradingInstructions) {
}
