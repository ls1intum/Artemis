package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for AI-generated structured assessment criteria.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Context used to generate structured assessment criteria")
public record AssessmentCriteriaGenerationRequestDTO(@NotBlank @Size(max = 50_000) @Schema(description = "Current problem statement") String problemStatement,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Schema(description = "Maximum regular score") Double maxPoints,
        @NotNull @DecimalMin("0") @Schema(description = "Maximum bonus score") Double bonusPoints,
        @Nullable @Size(max = 50_000) @Schema(description = "General assessment instructions") String gradingInstructions,
        @Nullable @Size(max = 200_000) @Schema(description = "Current example solution, if available") String exampleSolution,
        @Nullable @Size(max = 200_000) @Schema(description = "Optional exercise-specific context appended to the default prompt") String additionalContext) {

    @AssertTrue(message = "Point values and their sum must be finite")
    @Schema(hidden = true)
    public boolean arePointValuesFinite() {
        return (maxPoints == null || Double.isFinite(maxPoints)) && (bonusPoints == null || Double.isFinite(bonusPoints))
                && (maxPoints == null || bonusPoints == null || Double.isFinite(maxPoints + bonusPoints));
    }
}
