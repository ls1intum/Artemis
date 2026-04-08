package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user an exercise got updated.
 */
@CourseNotificationType(8)
public class ExerciseUpdatedNotification extends CourseNotification {

    protected Long exerciseId;

    protected String exerciseTitle;

    protected Long examId;

    protected Long exerciseGroupId;

    protected String exerciseType;

    /**
     * Default constructor used when creating a new post notification.
     */
    public ExerciseUpdatedNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, Long examId, Long exerciseGroupId,
            String exerciseType) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.examId = examId;
        this.exerciseGroupId = exerciseGroupId;
        this.exerciseType = exerciseType;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ExerciseUpdatedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
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
        if (examId != null && exerciseGroupId != null && exerciseType != null) {
            return "/course-management/" + courseId + "/exams/" + examId + "/exercise-groups/" + exerciseGroupId + "/" + exerciseType + "-exercises/" + exerciseId;
        }
        return "/courses/" + courseId + "/exercises/" + exerciseId;
    }
}
