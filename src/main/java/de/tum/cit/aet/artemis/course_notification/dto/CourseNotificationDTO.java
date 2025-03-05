package de.tum.cit.aet.artemis.course_notification.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotificationCategory;

/**
 * Record to represent course notifications.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationDTO(String notificationType, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category, Map<String, String> parameters)
        implements Serializable {
}
