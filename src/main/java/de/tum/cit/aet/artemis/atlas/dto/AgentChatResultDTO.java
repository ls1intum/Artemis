package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message, competency modification flag, and optional preview data.
 */
public record AgentChatResultDTO(@NotNull String message, boolean competenciesModified, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview,
        @Nullable BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {

    /**
     * Constructor for simple responses without preview data.
     */
    public AgentChatResultDTO(String message, boolean competenciesModified) {
        this(message, competenciesModified, null, null);
    }
}
