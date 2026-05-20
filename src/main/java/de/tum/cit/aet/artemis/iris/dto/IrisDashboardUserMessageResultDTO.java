package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardUserMessageResultDTO(long userMessageId, long sessionId, @Nullable Long courseId, String sessionType, ZonedDateTime userSentAt,
        @Nullable String nextSender, @Nullable ZonedDateTime nextSentAt) implements Serializable {
}
