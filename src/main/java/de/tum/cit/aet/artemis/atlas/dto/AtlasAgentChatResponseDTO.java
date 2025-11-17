package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent chat responses.
 * Contains the agent's message, preview data for competencies, and metadata.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentChatResponseDTO(@NotBlank @Size(max = 10000) String message, @Nullable String sessionId, @NotNull ZonedDateTime timestamp, boolean success,
        boolean competenciesModified, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview, @Nullable BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {
}
