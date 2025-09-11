package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement rewrite responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing rewritten problem statement")
public record ProblemStatementRewriteResponseDTO(@NotNull @Schema(description = "Improved problem statement text") String rewrittenText,

        @NotNull @Schema(description = "Whether the text was actually improved", example = "true") Boolean improved) {
}
