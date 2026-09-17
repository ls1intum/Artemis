package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.TutorialGroupAssignedPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells a tutor they were assigned to a tutorial group.
 */
@CourseNotificationType(21)
public class TutorialGroupAssignedNotification extends CourseNotification {

    private final TutorialGroupAssignedPayloadDTO payload;

    /**
     * Default constructor used when creating the notification.
     */
    public TutorialGroupAssignedNotification(Long courseId, String courseTitle, String courseImageUrl, String groupTitle, Long groupId, String moderatorName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new TutorialGroupAssignedPayloadDTO(groupTitle, groupId, moderatorName);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public TutorialGroupAssignedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, TutorialGroupAssignedPayloadDTO.class);
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
        return "/courses/" + courseId + "/tutorial-groups/" + payload.groupId();
    }

    @Override
    public TutorialGroupAssignedPayloadDTO payload() {
        return payload;
    }
}
