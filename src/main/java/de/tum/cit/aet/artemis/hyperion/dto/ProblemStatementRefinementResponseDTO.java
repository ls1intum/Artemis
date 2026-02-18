package de.tum.cit.aet.artemis.hyperion.dto;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing refined problem statement")
public record ProblemStatementRefinementResponseDTO(@Schema(description = "Refined problem statement text") String refinedProblemStatement) {
}
