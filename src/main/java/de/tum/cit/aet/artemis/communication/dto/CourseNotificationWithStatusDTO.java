package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationWithStatusDTO(CourseNotification notification, UserCourseNotificationStatus status) {
}
