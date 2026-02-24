package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for FAQ rewrite responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing rewritten FAQ")
public record RewriteFaqResponseDTO(@NotNull String rewrittenText, @NotNull List<String> inconsistencies, @NotNull List<String> suggestions, @NotNull String improvement) {
}
