package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement refinement responses.
 * <p>
 * On success, {@code refinedProblemStatement} contains the refined text.
 * On error, an exception is thrown (BadRequestAlertException for validation
 * errors,
 * InternalServerErrorAlertException for AI/processing errors).
 *
 * @param refinedProblemStatement  the refined problem statement text
 * @param originalProblemStatement reserved for future use (currently always
 *                                     null)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing refined problem statement")
public record ProblemStatementRefinementResponseDTO(@Schema(description = "Refined problem statement text") String refinedProblemStatement,
        @Nullable @Schema(description = "Original problem statement (reserved for future use)") String originalProblemStatement) {

    /**
     * Constructor for successful refinement
     *
     * @param refinedProblemStatement the refined problem statement
     */
    public ProblemStatementRefinementResponseDTO(String refinedProblemStatement) {
        this(refinedProblemStatement, null);
    }
}
