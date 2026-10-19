package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.DeregisteredFromTutorialGroupPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells a student that they were deregistered from a tutorial group.
 */
@CourseNotificationType(24)
public class DeregisteredFromTutorialGroupNotification extends CourseNotification {

    private final DeregisteredFromTutorialGroupPayloadDTO payload;

    /**
     * Default constructor used when creating the notification.
     */
    public DeregisteredFromTutorialGroupNotification(Long courseId, String courseTitle, String courseImageUrl, String groupTitle, Long groupId, String moderatorName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new DeregisteredFromTutorialGroupPayloadDTO(groupTitle, groupId, moderatorName);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public DeregisteredFromTutorialGroupNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, DeregisteredFromTutorialGroupPayloadDTO.class);
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
        return "/courses/" + courseId;
    }

    @Override
    public DeregisteredFromTutorialGroupPayloadDTO payload() {
        return payload;
    }
}
