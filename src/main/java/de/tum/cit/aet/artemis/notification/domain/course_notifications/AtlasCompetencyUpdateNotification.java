package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.AtlasCompetencyUpdatePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells course instructors and administrators what an automatic Atlas competency orchestration run
 * changed, or that it failed.
 * <p>
 * E-mail only and switched off in every preset, so it reaches only the users who opted in for the course. It
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
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.GENERAL;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(14);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.EMAIL);
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
