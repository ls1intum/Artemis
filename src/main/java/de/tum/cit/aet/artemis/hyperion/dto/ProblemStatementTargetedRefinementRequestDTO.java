package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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

        @Min(1) @Max(10000) @Schema(description = "End column within end line (1-indexed, exclusive — points after the last selected character, optional for character-level targeting)") Integer endColumn,

        @NotBlank @Size(max = 500) @Schema(description = "Instruction describing what should change") String instruction) {

    /**
     * Validates that startLine <= endLine and startColumn < endColumn when on the same line.
     */
    public ProblemStatementTargetedRefinementRequestDTO {
        if (startLine != null && endLine != null && startLine > endLine) {
            throw new BadRequestAlertException("startLine must be less than or equal to endLine", "ProblemStatement", "ProblemStatementRefinement.invalidLineRange");
        }
        if ((startColumn == null) != (endColumn == null)) {
            throw new BadRequestAlertException("startColumn and endColumn must be either both null or both non-null", "ProblemStatement",
                    "ProblemStatementRefinement.invalidColumnRange");
        }
        if (startColumn != null && startLine.equals(endLine) && startColumn >= endColumn) {
            throw new BadRequestAlertException("startColumn must be strictly less than endColumn on the same line (endColumn is exclusive)", "ProblemStatement",
                    "ProblemStatementRefinement.invalidColumnRange");
        }
        if (instruction != null) {
            instruction = instruction.trim();
            if (instruction.isEmpty()) {
                throw new BadRequestAlertException("instruction must not be empty after trimming", "ProblemStatement", "ProblemStatementRefinement.instructionEmpty");
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
