package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the iris response notification.
 *
 * @param sessionId      the Iris chat session the answer belongs to
 * @param messagePreview a short plain-text preview of the answer
 * @param chatTitle      the title of the chat
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisResponsePayloadDTO(@Nullable Long sessionId, @Nullable String messagePreview, @Nullable String chatTitle) implements CourseNotificationPayloadDTO {
}
