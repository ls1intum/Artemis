package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for inline comments used in targeted problem statement refinement.
 * Each comment specifies a line range (and optionally column range) and an
 * instruction for the AI.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Inline comment for targeted refinement of specific lines or text ranges")
public record InlineCommentDTO(@NotNull @Min(1) @Max(10000) @Schema(description = "Start line number (1-indexed)") Integer startLine,

        @NotNull @Min(1) @Max(10000) @Schema(description = "End line number (1-indexed, inclusive)") Integer endLine,

        @Nullable @Min(1) @Max(10000) @Schema(description = "Start column within start line (1-indexed, optional for character-level targeting)") Integer startColumn,

        @Nullable @Min(1) @Max(10000) @Schema(description = "End column within end line (1-indexed, inclusive, optional for character-level targeting)") Integer endColumn,

        @NotBlank @Size(max = 500) @Schema(description = "Instruction describing what should change") String instruction) {

    /**
     * Validates that startLine <= endLine and startColumn <= endColumn when on the
     * same line.
     */
    public InlineCommentDTO {
        if (startLine != null && endLine != null && startLine > endLine) {
            throw new IllegalArgumentException("startLine must be less than or equal to endLine");
        }
        if (startColumn != null && endColumn != null && startLine != null && endLine != null && startLine.equals(endLine) && startColumn > endColumn) {
            throw new IllegalArgumentException("startColumn must be less than or equal to endColumn on the same line");
        }
    }

    /**
     * Returns true if this comment targets a specific character range (not just
     * lines).
     */
    public boolean hasColumnRange() {
        return startColumn != null && endColumn != null;
    }
}
