package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement refinement requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to rewrite a problem statement")
public record ProblemStatementRefinementRequestDTO(@NotNull @NotBlank @Schema(description = "Original problem statement text to be refined") String problemStatementText,
        @NotBlank @Size(max = 1000) @Schema(description = "User prompt describing the problem statement requirements") String userPrompt) {
}
