package de.tum.cit.aet.artemis.communication.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;

/**
 * Record to represent course notifications.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationDTO(String notificationType, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category, Map<String, Object> parameters)
        implements Serializable {
}
