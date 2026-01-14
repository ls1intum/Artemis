package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatResponseDTO(@NotBlank @Size(max = 8000) String message, @NotBlank String sessionId, @NotNull ZonedDateTime timestamp, boolean success,
        boolean competenciesModified) {
}
