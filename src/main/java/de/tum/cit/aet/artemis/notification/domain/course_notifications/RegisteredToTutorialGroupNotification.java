package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.dto.payload.RegisteredToTutorialGroupPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells a student that they were registered in a tutorial group.
 */
@CourseNotificationType(23)
public class RegisteredToTutorialGroupNotification extends CourseNotification {

    private final RegisteredToTutorialGroupPayloadDTO payload;

    /**
     * Default constructor used when creating the notification.
     */
    public RegisteredToTutorialGroupNotification(Long courseId, String courseTitle, String courseImageUrl, String groupTitle, Long groupId, String moderatorName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new RegisteredToTutorialGroupPayloadDTO(groupTitle, groupId, moderatorName);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public RegisteredToTutorialGroupNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, RegisteredToTutorialGroupPayloadDTO.class);
    }

    @Override
    protected CourseNotificationDeliveryPolicy getDeliveryPolicy() {
        return CourseNotificationDeliveryPolicy.GENERAL;
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/tutorial-groups/" + payload.groupId();
    }

    @Override
    public RegisteredToTutorialGroupPayloadDTO payload() {
        return payload;
    }
}
