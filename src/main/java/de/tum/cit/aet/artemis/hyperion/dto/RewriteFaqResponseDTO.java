package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for FAQ rewrite responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing rewritten FAQ")
public record RewriteFaqResponseDTO(String rewrittenText, List<String> inconsistencies, List<String> suggestions, String improvement) {
}
