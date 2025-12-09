package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for inline comments used in targeted problem statement refinement.
 * Each comment specifies a line range and an instruction for the AI.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Inline comment for targeted refinement of specific lines")
public record InlineCommentDTO(@NotNull @Min(1) @Max(10000) @Schema(description = "Start line number (1-indexed)") Integer startLine,

        @NotNull @Min(1) @Max(10000) @Schema(description = "End line number (1-indexed, inclusive)") Integer endLine,

        @NotBlank @Size(max = 500) @Schema(description = "Instruction describing what should change") String instruction) {

    /**
     * Validates that startLine <= endLine.
     */
    public InlineCommentDTO {
        if (startLine != null && endLine != null && startLine > endLine) {
            throw new IllegalArgumentException("startLine must be less than or equal to endLine");
        }
    }
}
