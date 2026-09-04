package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewExercisePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a new post in a channel of any type. Announcement posts and thread answers
 * are sent via different notifications.
 */
@CourseNotificationType(5)
public class NewExerciseNotification extends CourseNotification {

    private final NewExercisePayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewExerciseNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String difficulty, String releaseDate,
            String dueDate, Long numberOfPoints) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewExercisePayloadDTO(exerciseId, exerciseTitle, difficulty, releaseDate, dueDate, numberOfPoints);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewExerciseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewExercisePayloadDTO.class);
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
    public NewExercisePayloadDTO payload() {
        return payload;
    }
}
