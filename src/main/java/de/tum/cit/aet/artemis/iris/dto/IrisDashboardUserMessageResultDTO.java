package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

public record IrisDashboardUserMessageResultDTO(long userMessageId, long sessionId, @Nullable Long courseId, String sessionType, ZonedDateTime userSentAt,
        @Nullable String nextSender, @Nullable ZonedDateTime nextSentAt) {
}
