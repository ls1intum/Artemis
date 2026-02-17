package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement refinement responses.
 * <p>
 * On success, {@code refinedProblemStatement} contains the refined text.
 * On error, an exception is thrown (BadRequestAlertException for validation errors,
 * InternalServerErrorAlertException for AI/processing errors).
 *
 * @param refinedProblemStatement the refined problem statement text
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing refined problem statement")
public record ProblemStatementRefinementResponseDTO(@NotNull @Size(max = 50000) @Schema(description = "Refined problem statement text") String refinedProblemStatement) {
}
