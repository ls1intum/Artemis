package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardUserMessageResultDTO(long userMsgId, long sessionId, Instant sentAt, @Nullable IrisMessageSender nextSender, @Nullable Instant nextSentAt,
        @Nullable String modeLabel) {
}
