package de.tum.cit.aet.artemis.notification.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request to mark the notifications the client displayed as seen.
 *
 * @param notificationIds the ids of the displayed notifications
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSeenRequestDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> notificationIds) {
}
