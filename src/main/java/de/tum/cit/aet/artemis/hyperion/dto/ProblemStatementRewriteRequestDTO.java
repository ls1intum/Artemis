package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement rewrite requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to rewrite a problem statement")
public record ProblemStatementRewriteRequestDTO(@NotNull @NotBlank @Schema(description = "Original problem statement text to be improved") String problemStatementText) {
}
