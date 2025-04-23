package de.tum.cit.aet.artemis.communication.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationSerializedDTO(String notificationType, long notificationId, long courseId, String creationDate, CourseNotificationCategory category,
        Map<String, Object> parameters, UserCourseNotificationStatusType status) {

    public CourseNotificationSerializedDTO(CourseNotificationDTO courseNotificationDTO) {
        this(courseNotificationDTO.notificationType(), courseNotificationDTO.notificationId(), courseNotificationDTO.courseId(), courseNotificationDTO.creationDate().toString(),
                courseNotificationDTO.category(), courseNotificationDTO.parameters(), courseNotificationDTO.status());
    }
}
