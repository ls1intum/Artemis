package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Internal result object for Atlas Agent chat processing.
 * Contains the response message, competency modification flag, and optional preview data for both competencies and relations.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentChatResultDTO(@NotNull String message, @NotNull boolean competenciesModified, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview,
        @Nullable BatchCompetencyPreviewResponseDTO batchCompetencyPreview, @Nullable SingleRelationPreviewResponseDTO relationPreview,
        @Nullable BatchRelationPreviewResponseDTO batchRelationPreview, @Nullable RelationGraphPreviewDTO relationGraphPreview) {

    /**
     * Constructor for simple responses without preview data.
     */
    public AgentChatResultDTO(String message, boolean competenciesModified) {
        this(message, competenciesModified, null, null, null, null, null);
    }

    /**
     * Constructor for competency-only responses (backward compatibility).
     */
    public AgentChatResultDTO(String message, boolean competenciesModified, SingleCompetencyPreviewResponseDTO competencyPreview,
            BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {
        this(message, competenciesModified, competencyPreview, batchCompetencyPreview, null, null, null);
    }
}
