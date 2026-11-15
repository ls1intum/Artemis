package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.util.List;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;

/** Delivery channels, audience category and retention shared by related notification types. */
public enum CourseNotificationDeliveryPolicy {

    GENERAL(CourseNotificationCategory.GENERAL, 7, NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    EXERCISE(CourseNotificationCategory.GENERAL, 14, NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    COMMUNICATION(CourseNotificationCategory.COMMUNICATION, 7, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    ANNOUNCEMENT(CourseNotificationCategory.COMMUNICATION, 30, NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    FEEDBACK_REQUEST(CourseNotificationCategory.GENERAL, 14, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    IN_APP_GENERAL(CourseNotificationCategory.GENERAL, 7, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH),

    IRIS_REVIEW(CourseNotificationCategory.IRIS_REVIEW, 7, NotificationChannelOption.WEBAPP),

    COMPETENCY(CourseNotificationCategory.GENERAL, 14, NotificationChannelOption.EMAIL),

    IRIS_RESPONSE(CourseNotificationCategory.GENERAL, 7, NotificationChannelOption.PUSH),

    MENTION(CourseNotificationCategory.COMMUNICATION, 7, NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);

    private final CourseNotificationCategory category;

    private final Duration cleanupDuration;

    private final List<NotificationChannelOption> channels;

    CourseNotificationDeliveryPolicy(CourseNotificationCategory category, int retentionDays, NotificationChannelOption... channels) {
        this.category = category;
        this.cleanupDuration = Duration.ofDays(retentionDays);
        this.channels = List.of(channels);
    }

    CourseNotificationCategory category() {
        return category;
    }

    Duration cleanupDuration() {
        return cleanupDuration;
    }

    List<NotificationChannelOption> channels() {
        return channels;
    }
}
