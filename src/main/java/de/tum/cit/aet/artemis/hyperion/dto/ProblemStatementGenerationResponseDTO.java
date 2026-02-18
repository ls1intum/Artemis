package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement generation responses.
 * <p>
 * On success, {@code draftProblemStatement} contains the generated text (never null or empty).
 * Errors are communicated via standard Spring error responses (e.g. {@code 400}, {@code 500}).
 *
 * @param draftProblemStatement the generated problem statement text (non-empty on success, never null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing generated problem statement")
public record ProblemStatementGenerationResponseDTO(@Schema(description = "Draft problem statement text") String draftProblemStatement) {
}
