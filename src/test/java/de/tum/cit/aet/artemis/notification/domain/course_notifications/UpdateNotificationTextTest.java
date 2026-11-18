package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests that the message an editor writes for an exercise update or an attachment change travels with the notification, and
 * that notifications stored before it existed, or sent without one, still read back.
 */
class UpdateNotificationTextTest {

    private static final long COURSE_ID = 1L;

    private static final String NOTIFICATION_TEXT = "Please re-download, slide 4 was corrected";

    private static final ZonedDateTime CREATION_DATE = ZonedDateTime.parse("2026-10-11T10:00:00+02:00");

    @Test
    void exerciseUpdatedNotificationCarriesTheNotificationText() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, "Course", null, 10L, "Exercise", null, null, "text", NOTIFICATION_TEXT);

        assertThat(notification.payload().notificationText()).isEqualTo(NOTIFICATION_TEXT);
        assertThat(notification.getParameters()).containsEntry("notificationText", NOTIFICATION_TEXT).containsEntry("exerciseTitle", "Exercise");
    }

    @Test
    void exerciseUpdatedNotificationWithoutTextHasNoNotificationTextParameter() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, "Course", null, 10L, "Exercise", null, null, "text", null);

        // Absent rather than null, so that nothing is stored for it and clients see the same shape as before.
        assertThat(notification.getParameters()).doesNotContainKey("notificationText");
    }

    @Test
    void exerciseUpdatedNotificationReadsTheStoredNotificationTextBack() {
        var parameters = Map.of("exerciseId", "10", "exerciseTitle", "Exercise", "exerciseType", "text", "notificationText", NOTIFICATION_TEXT);

        var notification = new ExerciseUpdatedNotification(5L, COURSE_ID, CREATION_DATE, parameters);

        assertThat(notification.payload().notificationText()).isEqualTo(NOTIFICATION_TEXT);
    }

    @Test
    void exerciseUpdatedNotificationStoredWithoutNotificationTextStillReadsBack() {
        // A notification stored before the text was delivered has no row for it.
        var parameters = Map.of("exerciseId", "10", "exerciseTitle", "Exercise", "exerciseType", "text");

        var notification = new ExerciseUpdatedNotification(5L, COURSE_ID, CREATION_DATE, parameters);

        assertThat(notification.payload().notificationText()).isNull();
        assertThat(notification.payload().exerciseTitle()).isEqualTo("Exercise");
        assertThat(notification.getParameters()).doesNotContainKey("notificationText");
    }

    @Test
    void attachmentChangedNotificationCarriesTheNotificationText() {
        var notification = new AttachmentChangedNotification(COURSE_ID, "Course", null, "Slides", "Lecture 1", null, 20L, NOTIFICATION_TEXT);

        assertThat(notification.payload().notificationText()).isEqualTo(NOTIFICATION_TEXT);
        assertThat(notification.getParameters()).containsEntry("notificationText", NOTIFICATION_TEXT).containsEntry("unitName", "Lecture 1");
    }

    @Test
    void attachmentChangedNotificationWithoutTextHasNoNotificationTextParameter() {
        var notification = new AttachmentChangedNotification(COURSE_ID, "Course", null, "Slides", "Lecture 1", null, 20L, null);

        assertThat(notification.getParameters()).doesNotContainKey("notificationText");
    }

    @Test
    void attachmentChangedNotificationReadsTheStoredNotificationTextBack() {
        var parameters = Map.of("attachmentName", "Slides", "unitName", "Lecture 1", "lectureId", "20", "notificationText", NOTIFICATION_TEXT);

        var notification = new AttachmentChangedNotification(5L, COURSE_ID, CREATION_DATE, parameters);

        assertThat(notification.payload().notificationText()).isEqualTo(NOTIFICATION_TEXT);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/lectures/20");
    }

    @Test
    void attachmentChangedNotificationStoredWithoutNotificationTextStillReadsBack() {
        var parameters = Map.of("attachmentName", "Slides", "unitName", "Lecture 1", "lectureId", "20");

        var notification = new AttachmentChangedNotification(5L, COURSE_ID, CREATION_DATE, parameters);

        assertThat(notification.payload().notificationText()).isNull();
        assertThat(notification.payload().unitName()).isEqualTo("Lecture 1");
        assertThat(notification.getParameters()).doesNotContainKey("notificationText");
    }
}
