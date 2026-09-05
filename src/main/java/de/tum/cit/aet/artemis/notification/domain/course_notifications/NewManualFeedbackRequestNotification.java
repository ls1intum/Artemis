package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewManualFeedbackRequestPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that manual feedback was requested.
 */
@CourseNotificationType(11)
public class NewManualFeedbackRequestNotification extends CourseNotification {

    private final NewManualFeedbackRequestPayloadDTO payload;

    /**
     * Default constructor used when creating a new manual feedback request notification.
     */
    public NewManualFeedbackRequestNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, Long examId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewManualFeedbackRequestPayloadDTO(exerciseId, exerciseTitle, examId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewManualFeedbackRequestNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewManualFeedbackRequestPayloadDTO.class);
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
        if (payload.examId() != null) {
            return "/course-management/" + courseId + "/exams/" + payload.examId() + "/assessment-dashboard/" + payload.exerciseId();
        }
        return "/course-management/" + courseId + "/assessment-dashboard/" + payload.exerciseId();
    }

    @Override
    public NewManualFeedbackRequestPayloadDTO payload() {
        return payload;
    }
}
