package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement refinement requests.
 * Supports two modes:
 * 1. Global refinement: userPrompt is provided
 * 2. Targeted refinement: inlineComments are provided
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to refine a problem statement")
public record ProblemStatementRefinementRequestDTO(@NotBlank @Schema(description = "Original problem statement text to be refined") String problemStatementText,

        @Nullable @Size(max = 1000) @Schema(description = "User prompt for global refinement (optional if inlineComments provided)") String userPrompt,

        @Nullable @Valid @Size(max = 10) @Schema(description = "Inline comments for targeted refinement of specific lines") List<InlineCommentDTO> inlineComments) {

    /**
     * Bean validation method that ensures exactly one of userPrompt or
     * inlineComments is provided.
     *
     * @return true if exactly one of userPrompt or inlineComments is present
     */
    @AssertTrue(message = "Either userPrompt or inlineComments must be provided exclusively")
    public boolean isExactlyOneRefinementModeProvided() {
        boolean hasUserPrompt = userPrompt != null && !userPrompt.isBlank();
        boolean hasInlineComments = inlineComments != null && !inlineComments.isEmpty();
        return hasUserPrompt ^ hasInlineComments;
    }

    /**
     * Returns true if this request uses inline comments mode.
     */
    public boolean hasInlineComments() {
        return inlineComments != null && !inlineComments.isEmpty();
    }
}
