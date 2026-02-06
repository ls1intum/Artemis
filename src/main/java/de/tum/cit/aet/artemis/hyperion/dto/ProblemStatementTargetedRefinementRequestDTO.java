package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for targeted problem statement refinement requests.
 * Used when applying selection-based instructions (Canvas-style).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to refine a problem statement using targeted selection-based instructions")
public record ProblemStatementTargetedRefinementRequestDTO(
        @NotBlank @Size(max = 50000) @Schema(description = "Original problem statement text to be refined (max 50,000 chars)") String problemStatementText,

        @NotNull @Min(1) @Max(10000) @Schema(description = "Start line number (1-indexed)") Integer startLine,

        @NotNull @Min(1) @Max(10000) @Schema(description = "End line number (1-indexed, inclusive)") Integer endLine,

        @Min(1) @Max(10000) @Schema(description = "Start column within start line (1-indexed, optional for character-level targeting)") Integer startColumn,

        @Min(1) @Max(10000) @Schema(description = "End column within end line (1-indexed, inclusive, optional for character-level targeting)") Integer endColumn,

        @Size(max = 500) @Schema(description = "Instruction describing what should change") String instruction) {

    /**
     * Validates that startLine <= endLine and startColumn <= endColumn when on the same line.
     */
    public ProblemStatementTargetedRefinementRequestDTO {
        if (startLine != null && endLine != null && startLine > endLine) {
            throw new IllegalArgumentException("startLine must be less than or equal to endLine");
        }
        if ((startColumn == null) != (endColumn == null)) {
            throw new IllegalArgumentException("startColumn and endColumn must be either both null or both non-null");
        }
        if (startColumn != null && startLine.equals(endLine) && startColumn > endColumn) {
            throw new IllegalArgumentException("startColumn must be less than or equal to endColumn on the same line");
        }
        if (instruction != null) {
            instruction = instruction.trim();
            if (instruction.isEmpty()) {
                throw new IllegalArgumentException("instruction must not be empty after trimming");
            }
        }
    }

    /**
     * Returns true if this request targets a specific character range (not just lines).
     */
    public boolean hasColumnRange() {
        return startColumn != null && endColumn != null;
    }
}
