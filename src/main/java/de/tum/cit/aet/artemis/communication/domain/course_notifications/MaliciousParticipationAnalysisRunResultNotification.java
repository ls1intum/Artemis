package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that a manual Malicious Participation Analysis run has finished.
 */
@CourseNotificationType(26)
public class MaliciousParticipationAnalysisRunResultNotification extends CourseNotification {

    protected String scopeTitle;

    protected Long analyzed;

    protected Long maliciousCount;

    protected Long benignCount;

    protected Long failed;

    protected String maliciousSummary;

    public MaliciousParticipationAnalysisRunResultNotification(Long courseId, String courseTitle, String courseImageUrl, String scopeTitle, long analyzed, long maliciousCount,
            long benignCount, long failed) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.scopeTitle = scopeTitle;
        this.analyzed = analyzed;
        this.maliciousCount = maliciousCount;
        this.benignCount = benignCount;
        this.failed = failed;
        this.maliciousSummary = maliciousCount > 0 ? " M:" + maliciousCount : "";
    }

    public MaliciousParticipationAnalysisRunResultNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId;
    }
}
