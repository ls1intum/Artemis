package de.tum.cit.aet.artemis.notification.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;

/**
 * Record to represent course notifications.
 * <p>
 * The type specific values live in {@link CourseNotificationPayloadDTO}, one record per notification type, so that a
 * reader knows what a notification of a given {@code notificationType} carries. A client narrows the payload on that
 * field rather than reaching into a map of objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category,
        String courseTitle, String courseIconUrl, CourseNotificationPayloadDTO payload, UserCourseNotificationStatusType status, String relativeWebAppUrl) implements Serializable {

    public CourseNotificationDTO(String notificationType, long notificationId, long courseId, ZonedDateTime creationDate, CourseNotificationCategory category, String courseTitle,
            String courseIconUrl, CourseNotificationPayloadDTO payload, String relativeWebAppUrl) {
        this(notificationType, notificationId, courseId, creationDate, category, courseTitle, courseIconUrl, payload, UserCourseNotificationStatusType.SEEN, relativeWebAppUrl);
    }
}
