package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackChannelRequestDTO(ChannelDTO channel, List<String> feedbackDetailTexts, String testCaseName) {
}
