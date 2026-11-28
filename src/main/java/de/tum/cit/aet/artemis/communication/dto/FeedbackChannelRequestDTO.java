package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request to create a channel for discussing feedback on a programming exercise.
 *
 * @param channel             the channel settings
 * @param feedbackDetailTexts the feedback details to include in the channel
 * @param testCaseName        the test case name, when available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackChannelRequestDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChannelDTO channel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> feedbackDetailTexts, String testCaseName) {
}
