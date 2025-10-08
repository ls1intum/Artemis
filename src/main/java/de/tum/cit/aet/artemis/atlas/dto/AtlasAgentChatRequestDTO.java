package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatRequestDTO(

        @NotBlank @Size(max = 8000) String message,

        @NotNull String sessionId

) {
}
