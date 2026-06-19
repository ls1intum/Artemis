package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;

/**
 * Result row for a user message and how it was followed up.
 * <p>
 * {@code nextSender}/{@code nextSentAt} describe the immediate next message (any sender) and are used for response-time computation.
 * {@code hasAssistantResponse} indicates whether any later assistant message (LLM or ARTIFACT) exists in the session and is the basis for
 * no-response detection: a user message counts as "no response" when {@code hasAssistantResponse} is {@code false}. Using the immediate next
 * message alone would wrongly treat a following user message as a response and undercount the no-response rate.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardUserMessageResultDTO(long userMsgId, long sessionId, Instant sentAt, @Nullable IrisMessageSender nextSender, @Nullable Instant nextSentAt,
        @Nullable String modeLabel, boolean hasAssistantResponse) {
}
