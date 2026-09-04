package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.ProgrammingBuildRunUpdatePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that a new programming build update is available.
 */
@CourseNotificationType(15)
public class ProgrammingBuildRunUpdateNotification extends CourseNotification {

    private final ProgrammingBuildRunUpdatePayloadDTO payload;

    /**
     * Default constructor used when creating the notification
     */
    public ProgrammingBuildRunUpdateNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, Long examId,
            Long exerciseGroupId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new ProgrammingBuildRunUpdatePayloadDTO(exerciseId, exerciseTitle, examId, exerciseGroupId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ProgrammingBuildRunUpdateNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, ProgrammingBuildRunUpdatePayloadDTO.class);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.GENERAL;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(7);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        if (payload.examId() != null && payload.exerciseGroupId() != null) {
            return "/course-management/" + courseId + "/exams/" + payload.examId() + "/exercise-groups/" + payload.exerciseGroupId() + "/programming-exercises/"
                    + payload.exerciseId();
        }
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public ProgrammingBuildRunUpdatePayloadDTO payload() {
        return payload;
    }
}
