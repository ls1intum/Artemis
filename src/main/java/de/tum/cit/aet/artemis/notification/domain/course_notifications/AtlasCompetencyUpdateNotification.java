package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.dto.payload.AtlasCompetencyUpdatePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells course instructors and administrators what an automatic Atlas competency orchestration run
 * changed, or that it failed.
 * <p>
 * E-mail only. The default and all-activity presets switch it on and the ignore preset switches it off, so recipients
 * can opt out per course. It
 * complements the transient websocket summary of the same run, which only reaches users who have the course open.
 */
@CourseNotificationType(27)
public class AtlasCompetencyUpdateNotification extends CourseNotification {

    private final AtlasCompetencyUpdatePayloadDTO payload;

    /**
     * Default constructor used when creating a new notification.
     */
    public AtlasCompetencyUpdateNotification(Long courseId, String courseTitle, String courseImageUrl, AtlasCompetencyUpdatePayloadDTO payload) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = payload;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public AtlasCompetencyUpdateNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, AtlasCompetencyUpdatePayloadDTO.class);
    }

    @Override
    protected CourseNotificationDeliveryPolicy getDeliveryPolicy() {
        return CourseNotificationDeliveryPolicy.COMPETENCY;
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/course-management/" + courseId + "/competency-management";
    }

    @Override
    public AtlasCompetencyUpdatePayloadDTO payload() {
        return payload;
    }
}
