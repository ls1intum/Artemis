package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement refinement responses.
 * <p>
 * Contract:
 * <ul>
 * <li><strong>Success case:</strong> {@code refinedProblemStatement} contains the generated text (never null or empty),
 * and {@code originalProblemStatement} is null.</li>
 * <li><strong>Error case:</strong> {@code refinedProblemStatement} is an empty string (""),
 * and {@code originalProblemStatement} contains the original problem statement (never null).</li>
 * </ul>
 * <p>
 * Note: The {@code refinedProblemStatement} field is never null - it will be either a non-empty string (success)
 * or an empty string (error). Callers should check if {@code originalProblemStatement} is null to determine success/failure,
 * or check if {@code refinedProblemStatement.isEmpty()} for the error case.
 *
 * @param refinedProblemStatement  the refined problem statement text (non-empty on success, empty string on error, never null)
 * @param originalProblemStatement original problem statement text if refinement failed (null on success, non-null on error)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing generated problem statement")
public record ProblemStatementRefinementResponseDTO(@Schema(description = "Refined problem statement text") String refinedProblemStatement,
        @Nullable @Schema(description = "Original problem statement if refinement failed") String originalProblemStatement) {

    /**
     * Constructor for successful refinement
     *
     * @param refinedProblemStatement the refined problem statement
     */
    public ProblemStatementRefinementResponseDTO(String refinedProblemStatement) {
        this(refinedProblemStatement, null);
    }
}
