package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationWithStatusDTO(CourseNotification notification, UserCourseNotificationStatus status) {
}
