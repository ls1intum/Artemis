package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

// TODO: Follow up - Integrate learning objectives to generate draft problem statement
/**
 * DTO for problem statement generation requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to generate a problem statement")
public record ProblemStatementGenerationRequestDTO(
        @NotBlank @Size(max = 1000) @Schema(description = "User prompt describing the problem statement requirements") String userPrompt) {
}
