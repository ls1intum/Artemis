package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement generation responses.
 * <p>
 * Contract:
 * <ul>
 * <li><strong>Success case:</strong> {@code draftProblemStatement} contains the generated text (never null or empty),
 * and {@code error} is null.</li>
 * <li><strong>Error case:</strong> {@code draftProblemStatement} is an empty string (""),
 * and {@code error} contains the error message (never null).</li>
 * </ul>
 * <p>
 * Note: The {@code draftProblemStatement} field is never null - it will be either a non-empty string (success)
 * or an empty string (error). Callers should check if {@code error} is null to determine success/failure,
 * or check if {@code draftProblemStatement.isEmpty()} for the error case.
 *
 * @param draftProblemStatement the generated problem statement text (non-empty on success, empty string on error, never null)
 * @param error                 error message if generation failed (null on success, non-null on error)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing generated problem statement")
public record ProblemStatementGenerationResponseDTO(@Schema(description = "Draft problem statement text") String draftProblemStatement,
        @Nullable @Schema(description = "Error message if generation failed") String error) {

    /**
     * Constructor for successful generation
     *
     * @param draftProblemStatement the generated problem statement
     */
    public ProblemStatementGenerationResponseDTO(String draftProblemStatement) {
        this(draftProblemStatement, null);
    }
}
