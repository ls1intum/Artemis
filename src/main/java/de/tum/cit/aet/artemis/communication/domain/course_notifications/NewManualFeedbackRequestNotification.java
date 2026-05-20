package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that manual feedback was requested.
 */
@CourseNotificationType(11)
public class NewManualFeedbackRequestNotification extends CourseNotification {

    protected Long exerciseId;

    protected String exerciseTitle;

    protected Long examId;

    /**
     * Default constructor used when creating a new manual feedback request notification.
     */
    public NewManualFeedbackRequestNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, Long examId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.examId = examId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewManualFeedbackRequestNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        if (examId != null) {
            return "/course-management/" + courseId + "/exams/" + examId + "/assessment-dashboard/" + exerciseId;
        }
        return "/course-management/" + courseId + "/assessment-dashboard/" + exerciseId;
    }
}
