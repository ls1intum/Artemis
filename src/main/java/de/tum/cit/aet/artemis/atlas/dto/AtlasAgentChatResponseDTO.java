package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatResponseDTO(

        String message,

        String sessionId,

        ZonedDateTime timestamp,

        boolean success

) {
}
