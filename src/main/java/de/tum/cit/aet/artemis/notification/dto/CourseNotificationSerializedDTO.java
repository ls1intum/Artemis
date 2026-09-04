package de.tum.cit.aet.artemis.notification.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationSerializedDTO(String notificationType, long notificationId, long courseId, String creationDate, CourseNotificationCategory category,
        Map<String, Object> parameters, UserCourseNotificationStatusType status) {

    public CourseNotificationSerializedDTO(CourseNotificationDTO courseNotificationDTO) {
        this(courseNotificationDTO.notificationType(), courseNotificationDTO.notificationId(), courseNotificationDTO.courseId(), courseNotificationDTO.creationDate().toString(),
                courseNotificationDTO.category(), flatten(courseNotificationDTO), courseNotificationDTO.status());
    }

    /**
     * Flattens the payload into the shape the mobile clients read.
     * <p>
     * Deliberately still a map here, unlike everywhere else: this record is the body of a push notification, so its
     * shape is a contract with released Android and iOS versions, and {@code PushNotificationDataDTO} carries the
     * version that would have to be raised to change it. The values are typed on the way in, which is the point.
     *
     * @param courseNotificationDTO the notification being pushed
     * @return the payload components and the values every notification carries, by name
     */
    private static Map<String, Object> flatten(CourseNotificationDTO courseNotificationDTO) {
        Map<String, Object> values = CourseNotificationPayloads.asMap(courseNotificationDTO.payload());
        values.put("courseTitle", courseNotificationDTO.courseTitle());
        values.put("courseIconUrl", courseNotificationDTO.courseIconUrl());
        return values;
    }
}
