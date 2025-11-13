package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement generation responses.
 *
 * @param draftProblemStatement the generated problem statement text, or empty if generation failed
 * @param error                 optional error message if generation failed
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
