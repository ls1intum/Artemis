package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackChannelRequestDTO(ChannelDTO channel, String feedbackDetailText) {
}
