package de.tum.cit.aet.artemis.atlas.service;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.atlas.dto.BatchCompetencyPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.SingleCompetencyPreviewResponseDTO;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message, competency modification flag, and optional preview data.
 */
public record AgentChatResult(@NotNull String message, boolean competenciesModified, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview,
        @Nullable BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {

    /**
     * Constructor for simple responses without preview data.
     */
    public AgentChatResult(String message, boolean competenciesModified) {
        this(message, competenciesModified, null, null);
    }
}
