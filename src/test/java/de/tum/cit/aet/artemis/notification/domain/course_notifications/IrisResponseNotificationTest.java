package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;

/**
 * Unit tests for {@link IrisResponseNotification}: channel support, category, cleanup duration and the
 * parameter round-trip (serialization of the protected fields and re-hydration from the database map).
 */
class IrisResponseNotificationTest {

    private static final Long COURSE_ID = 1L;

    private static final String COURSE_TITLE = "Test Course";

    private static final String COURSE_ICON = "icon.png";

    private static final Long SESSION_ID = 42L;

    private static final String MESSAGE_PREVIEW = "Iris has answered your message";

    private static final String CHAT_TITLE = "My Chat";

    private IrisResponseNotification newNotification() {
        return new IrisResponseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, SESSION_ID, MESSAGE_PREVIEW, CHAT_TITLE);
    }

    @Test
    void shouldOnlySupportPushChannel() {
        assertThat(newNotification().getSupportedChannels()).containsExactly(NotificationChannelOption.PUSH);
    }

    @Test
    void shouldBeInGeneralCategory() {
        assertThat(newNotification().getCourseNotificationCategory()).isEqualTo(CourseNotificationCategory.GENERAL);
    }

    @Test
    void shouldCleanUpAfterSevenDays() {
        assertThat(newNotification().getCleanupDuration()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldDeclareUniqueDatabaseTypeIdentifier() {
        assertThat(IrisResponseNotification.class.getAnnotation(CourseNotificationType.class).value()).isEqualTo(26);
    }

    @Test
    void shouldExposeReadableNotificationType() {
        assertThat(newNotification().getReadableNotificationType()).isEqualTo("irisResponseNotification");
    }

    @Test
    void shouldSerializeAllParameters() {
        var parameters = newNotification().getParameters();

        assertThat(parameters).containsEntry("sessionId", SESSION_ID).containsEntry("messagePreview", MESSAGE_PREVIEW).containsEntry("chatTitle", CHAT_TITLE)
                .containsEntry("courseTitle", COURSE_TITLE).containsEntry("courseIconUrl", COURSE_ICON);
    }

    @Test
    void shouldRehydrateFromDatabaseParameters() {
        var creationDate = ZonedDateTime.now();
        Map<String, String> parameters = Map.of("sessionId", String.valueOf(SESSION_ID), "messagePreview", MESSAGE_PREVIEW, "chatTitle", CHAT_TITLE, "courseTitle", COURSE_TITLE,
                "courseIconUrl", COURSE_ICON);

        var notification = new IrisResponseNotification(7L, COURSE_ID, creationDate, parameters);

        assertThat(notification.notificationId).isEqualTo(7L);
        assertThat(notification.courseId).isEqualTo(COURSE_ID);
        assertThat(notification.creationDate).isEqualTo(creationDate);
        assertThat(notification.getParameters()).containsEntry("sessionId", SESSION_ID).containsEntry("messagePreview", MESSAGE_PREVIEW).containsEntry("chatTitle", CHAT_TITLE);
    }
}
