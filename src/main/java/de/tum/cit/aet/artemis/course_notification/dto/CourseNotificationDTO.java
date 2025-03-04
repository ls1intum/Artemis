package de.tum.cit.aet.artemis.course_notification.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotificationCategory;

/**
 * Record to represent course notifications.
 */
public record CourseNotificationDTO(String notificationType, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category, Map<String, String> parameters)
        implements Serializable {
}
