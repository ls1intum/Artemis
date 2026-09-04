package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloads;
import de.tum.cit.aet.artemis.notification.dto.payload.ExerciseUpdatedPayload;

/**
 * Notification that tells the user an exercise got updated.
 */
@CourseNotificationType(8)
public class ExerciseUpdatedNotification extends CourseNotification {

    private final ExerciseUpdatedPayload payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public ExerciseUpdatedNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, Long examId, Long exerciseGroupId,
            String exerciseType) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new ExerciseUpdatedPayload(exerciseId, exerciseTitle, examId, exerciseGroupId, exerciseType);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ExerciseUpdatedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, ExerciseUpdatedPayload.class);
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
        if (payload.examId() != null && payload.exerciseGroupId() != null && payload.exerciseType() != null) {
            return "/course-management/" + courseId + "/exams/" + payload.examId() + "/exercise-groups/" + payload.exerciseGroupId() + "/" + payload.exerciseType() + "-exercises/"
                    + payload.exerciseId();
        }
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public ExerciseUpdatedPayload payload() {
        return payload;
    }
}
