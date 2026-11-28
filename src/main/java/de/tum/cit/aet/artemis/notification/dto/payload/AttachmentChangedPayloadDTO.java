package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the attachment changed notification.
 *
 * @param attachmentName   the attachment that changed
 * @param unitName         the lecture unit holding it
 * @param exerciseId       the exercise involved
 * @param lectureId        the lecture involved
 * @param notificationText the message the editor wrote for students about the change, absent when they wrote none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentChangedPayloadDTO(@Nullable String attachmentName, @Nullable String unitName, @Nullable Long exerciseId, @Nullable Long lectureId,
        @Nullable String notificationText) implements CourseNotificationPayloadDTO {
}
