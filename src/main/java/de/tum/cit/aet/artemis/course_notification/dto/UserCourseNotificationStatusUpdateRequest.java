package de.tum.cit.aet.artemis.course_notification.dto;

import java.util.List;

import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationStatusType;

/**
 * Record for notification status update requests
 */
public record UserCourseNotificationStatusUpdateRequest(List<Long> notificationIds, UserCourseNotificationStatusType statusType) {
}
