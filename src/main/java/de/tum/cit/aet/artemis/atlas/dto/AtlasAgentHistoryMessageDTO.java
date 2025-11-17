package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing a single message in the conversation history.
 *
 * @param content                The message content
 * @param isUser                 Whether the message is from the user (true) or the agent (false)
 * @param competencyPreview      Optional single competency preview data
 * @param batchCompetencyPreview Optional batch competency preview data
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentHistoryMessageDTO(String content, boolean isUser, @Nullable SingleCompetencyPreviewResponseDTO competencyPreview,
        @Nullable BatchCompetencyPreviewResponseDTO batchCompetencyPreview) {

}
