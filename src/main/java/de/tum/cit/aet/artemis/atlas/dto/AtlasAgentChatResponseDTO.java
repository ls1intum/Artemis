package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat responses.
 * Contains the agent's message, preview data for competencies, and metadata.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatResponseDTO(@NotBlank @Size(max = 10000) String message, @NotNull String sessionId, @NotNull ZonedDateTime timestamp, boolean success,
        boolean competenciesModified, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview, @NotEmpty BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {
}
