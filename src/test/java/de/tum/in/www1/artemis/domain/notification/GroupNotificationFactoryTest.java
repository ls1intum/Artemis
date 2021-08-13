package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class GroupNotificationFactoryTest {

    @Mock
    private User user;

    @Mock
    private Lecture lecture;

    @Mock
    private GroupNotificationType groupNotificationType;

    private String expectedTarget;

    private NotificationType notificationType;

    private String notificationText = "notification text";

    private Course course = new Course();

    private Attachment attachment = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example.txt").name("example");

    private GroupNotificationFactory groupNotificationFactory = mock(GroupNotificationFactory.class, CALLS_REAL_METHODS);

    // Based on Attachment

    private void initAttachmentTestCase() {
        // prepare method input parameters
        course.setId(1L);
        lecture = new Lecture().title("test").description("test").course(course);
        attachment.setLecture(lecture);
        notificationType = NotificationType.ATTACHMENT_CHANGE;
    }

    private void checkCreatedNotificationForAttachmentTestCase(GroupNotification createdNotification, String expectedText, String expectedTarget) {
        assertThat(createdNotification.getTitle()).isEqualTo("Attachment updated");
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getPriority()).isEqualTo(NotificationPriority.MEDIUM);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
    }

    @Test
    public void createNotificationBasedOnAttachment_withNotificationText() {

        initAttachmentTestCase();

        GroupNotification createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);

        expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":null,\"entity\":\"lectures\",\"course\":1,\"mainPage\":\"courses\"}";

        checkCreatedNotificationForAttachmentTestCase(createdNotification, notificationText, expectedTarget);
    }

    @Test
    public void createNotificationBasedOnAttachment_withoutNotificationText() {

        initAttachmentTestCase();

        GroupNotification createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, null);

        String expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":null,\"entity\":\"lectures\",\"course\":1,\"mainPage\":\"courses\"}";
        String expectedText = "Attachment \"" + attachment.getName() + "\" updated.";

        checkCreatedNotificationForAttachmentTestCase(createdNotification, expectedText, expectedTarget);
    }

    // Based on Exercise

    private void checkCreatedNotificationForExerciseTestCase(GroupNotification createdNotification, String expectedTitle, String expectedText, String expectedTarget) {
        assertThat(createdNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getPriority()).isEqualTo(NotificationPriority.MEDIUM);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
    }

    /*
     * @Test public void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated(){ notificationType = NotificationType.EXERCISE_CREATED; GroupNotification
     * createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);
     * checkCreatedNotificationForExerciseTestCase(createdNotification, "Exercise created", ); }
     */
}
