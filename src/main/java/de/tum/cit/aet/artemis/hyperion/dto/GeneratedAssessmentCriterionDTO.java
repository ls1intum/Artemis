package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One generated structured assessment criterion.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Generated structured assessment criterion")
public record GeneratedAssessmentCriterionDTO(@NotBlank @Size(max = 255) @Schema(description = "Criterion title") String title,
        @Schema(description = "Whether the criterion awards bonus points", requiredMode = Schema.RequiredMode.REQUIRED) boolean bonus,
        @NotNull @Size(min = 3, max = 3) @Schema(description = "Full-credit, partial-credit, and no-credit instructions, in that order") List<@Valid GeneratedAssessmentInstructionDTO> structuredGradingInstructions) {
}
