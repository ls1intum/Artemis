package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseOpenForPracticePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that an exercise quiz is open for practice.
 */
@CourseNotificationType(6)
public class ExerciseOpenForPracticeNotification extends CourseNotification {

    private final ExerciseOpenForPracticePayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public ExerciseOpenForPracticeNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new ExerciseOpenForPracticePayloadDTO(exerciseId, exerciseTitle);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ExerciseOpenForPracticeNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, ExerciseOpenForPracticePayloadDTO.class);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.GENERAL;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(14);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public ExerciseOpenForPracticePayloadDTO payload() {
        return payload;
    }
}
