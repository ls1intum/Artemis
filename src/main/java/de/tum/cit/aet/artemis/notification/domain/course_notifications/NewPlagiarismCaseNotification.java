package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewPlagiarismCasePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that a new plagiarism case was found.
 */
@CourseNotificationType(14)
public class NewPlagiarismCaseNotification extends CourseNotification {

    private final NewPlagiarismCasePayloadDTO payload;

    /**
     * Default constructor used when creating a new cpc plagiarism case notification
     */
    public NewPlagiarismCaseNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String exerciseType,
            String postMarkdownContent, Long examId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewPlagiarismCasePayloadDTO(exerciseId, exerciseTitle, exerciseType, postMarkdownContent, examId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewPlagiarismCaseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewPlagiarismCasePayloadDTO.class);
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
        if (payload.examId() != null) {
            return "/courses/" + courseId + "/exams/" + payload.examId();
        }
        return "/courses/" + courseId + "/exercises/" + payload.exerciseId();
    }

    @Override
    public NewPlagiarismCasePayloadDTO payload() {
        return payload;
    }
}
