package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the attachment changed notification.
 *
 * @param attachmentName the attachment that changed
 * @param unitName       the lecture unit holding it
 * @param exerciseId     the exercise involved
 * @param lectureId      the lecture involved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttachmentChangedPayloadDTO(String attachmentName, String unitName, Long exerciseId, Long lectureId) implements CourseNotificationPayloadDTO {
}
