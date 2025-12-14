package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat responses.
 * Contains the agent's message, timestamp, modification flags, and competency preview data.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatResponseDTO(@NotBlank @Size(max = 10000) String message, @NotNull ZonedDateTime timestamp, boolean competenciesModified,
        List<CompetencyPreviewDTO> competencyPreviews) {
}
