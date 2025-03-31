package de.tum.cit.aet.artemis.communication.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;

/**
 * Record to represent course notifications.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category,
        Map<String, Object> parameters, UserCourseNotificationStatusType status, String relativeWebAppUrl) implements Serializable {

    public CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category,
            Map<String, Object> parameters, String relativeWebAppUrl) {
        this(notificationType, notificationId, courseId, creationDate, category, parameters, UserCourseNotificationStatusType.SEEN, relativeWebAppUrl);
    }
}
