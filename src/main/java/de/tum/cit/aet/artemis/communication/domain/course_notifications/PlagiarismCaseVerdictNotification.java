package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that they received a plagiarism case verdict.
 */
@CourseNotificationType(17)
public class PlagiarismCaseVerdictNotification extends CourseNotification {

    protected Long exerciseId;

    protected String exerciseTitle;

    protected String exerciseType;

    protected String verdict;

    protected Long examId;

    /**
     * Default constructor used when creating the notification
     */
    public PlagiarismCaseVerdictNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String exerciseType, String verdict,
            Long examId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.exerciseType = exerciseType;
        this.verdict = verdict;
        this.examId = examId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public PlagiarismCaseVerdictNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
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
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        if (examId != null) {
            return "/courses/" + courseId + "/exams/" + examId;
        }
        return "/courses/" + courseId + "/exercises/" + exerciseId;
    }
}
