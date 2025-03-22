package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;

/**
 * Record for notification status update requests
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationStatusUpdateRequestDTO(List<Long> notificationIds, UserCourseNotificationStatusType statusType) {
}
