package de.tum.cit.aet.artemis.notification.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request to mark the notifications the client displayed as seen.
 *
 * @param notificationIds the ids of the displayed notifications
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSeenRequestDTO(List<Long> notificationIds) {
}
