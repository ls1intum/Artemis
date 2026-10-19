package de.tum.cit.aet.artemis.notification.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;

/**
 * DTO projection for course notifications together with the requesting user's status.
 *
 * @param notificationId   the unique identifier of the course notification
 * @param courseId         the identifier of the course the notification belongs to
 * @param notificationType the internal notification type identifier
 * @param creationDate     the date when the notification was created
 * @param status           the requesting user's status for the notification
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationWithStatusDTO(Long notificationId, Long courseId, Short notificationType, ZonedDateTime creationDate, UserCourseNotificationStatusType status) {
}
