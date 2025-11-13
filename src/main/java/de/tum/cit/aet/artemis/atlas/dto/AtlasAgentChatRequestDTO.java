package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat requests.
 * The sessionId is generated server-side based on courseId and userId for security,
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatRequestDTO(@NotBlank @Size(max = 8000) String message, @Nullable String sessionId) {
}
