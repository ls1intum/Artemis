package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for global problem statement refinement requests.
 * Used when applying a user prompt to the entire problem statement.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to refine a problem statement globally using a user prompt")
public record ProblemStatementGlobalRefinementRequestDTO(
        @NotBlank @Size(max = 50000) @Schema(description = "Original problem statement text to be refined (max 50,000 chars)") String problemStatementText,

        @Nullable @Size(max = 1000) @Schema(description = "User prompt for global refinement") String userPrompt) {
}
