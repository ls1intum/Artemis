package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One generated structured grading instruction.
 */
@Schema(description = "Generated structured grading instruction")
public record GeneratedAssessmentInstructionDTO(@DecimalMin("0") @Schema(description = "Nonnegative credits awarded") double credits,
        @NotBlank @Size(max = 255) @Schema(description = "Grading scale label") String gradingScale,
        @NotBlank @Schema(description = "Assessment instruction") String instructionDescription, @NotBlank @Schema(description = "Suggested feedback") String feedback,
        @Min(0) @Schema(description = "Maximum number of uses") int usageCount) {
}
