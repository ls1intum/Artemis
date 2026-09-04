package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloads;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseAssessedPayload;

/**
 * Notification that tells the user that their exercise was graded.
 */
@CourseNotificationType(7)
public class ExerciseAssessedNotification extends CourseNotification {

    private final ExerciseAssessedPayload payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public ExerciseAssessedNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String exerciseType, Long numberOfPoints,
            Long score, Long examId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new ExerciseAssessedPayload(exerciseId, exerciseTitle, exerciseType, numberOfPoints, score, examId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ExerciseAssessedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, ExerciseAssessedPayload.class);
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
        if (payload.examId() != null) {
            return "/courses/" + courseId + "/exams/" + payload.examId();
        }
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public ExerciseAssessedPayload payload() {
        return payload;
    }
}
