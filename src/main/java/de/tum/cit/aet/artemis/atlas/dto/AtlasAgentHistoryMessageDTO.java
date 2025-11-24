package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing a single message in the conversation history.
 *
 * @param content            The message content
 * @param isUser             Whether the message is from the user (true) or the agent (false)
 * @param competencyPreviews Optional list of competency preview data
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentHistoryMessageDTO(String content, boolean isUser, @Nullable List<CompetencyPreviewResponseDTO> competencyPreviews) {

}
