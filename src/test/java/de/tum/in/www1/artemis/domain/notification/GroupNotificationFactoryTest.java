package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class GroupNotificationFactoryTest {

    // @Mock
    // @Autowired
    private Attachment attachment;

    @Mock
    private User user;

    @Mock
    private Lecture lecture;

    @Mock
    private Course course;

    @Mock
    private GroupNotificationType groupNotificationType;

    @BeforeEach
    public void initTestCase() {
        attachment = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example.txt").name("example");

        course = new Course();
        course.setId(1L);
        lecture = new Lecture().title("test").description("test").course(course);
        attachment.setLecture(lecture);
    }

    @Test
    public void createNotificationBasedOnAttachment_withNotificationText() {

        GroupNotificationFactory groupNotificationFactory = mock(GroupNotificationFactory.class, CALLS_REAL_METHODS);

        // prepare method inputs
        NotificationType notificationType = NotificationType.ATTACHMENT_CHANGE;
        String notificationText = "notification text";

        // create notification
        GroupNotification notificationResult = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);

        // check if created notification is correct
        String expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":null,\"entity\":\"lectures\",\"course\":1,\"mainPage\":\"courses\"}";

        assertThat(notificationResult.getTitle()).isEqualTo("Attachment updated");
        assertThat(notificationResult.getText()).isEqualTo(notificationText);
        assertThat(notificationResult.getPriority()).isEqualTo(NotificationPriority.MEDIUM);
        assertThat(notificationResult.getAuthor()).isEqualTo(user);
        assertThat(notificationResult.getTarget()).isEqualTo(expectedTarget);
    }

    @Test
    public void createNotificationBasedOnAttachment_withoutNotificationText() {

        GroupNotificationFactory groupNotificationFactory = mock(GroupNotificationFactory.class, CALLS_REAL_METHODS);

        // prepare method inputs
        NotificationType notificationType = NotificationType.ATTACHMENT_CHANGE;

        // create notification
        GroupNotification notificationResult = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, null);

        System.out.println("HELLO");
        System.out.println(notificationResult.getText());
        System.out.println("EEED");

        // check if created notification is correct
        String expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":null,\"entity\":\"lectures\",\"course\":1,\"mainPage\":\"courses\"}";
        String expectedText = "Attachment \"" + attachment.getName() + "\" updated.";

        assertThat(notificationResult.getTitle()).isEqualTo("Attachment updated");
        assertThat(notificationResult.getText()).isEqualTo(expectedText);
        assertThat(notificationResult.getPriority()).isEqualTo(NotificationPriority.MEDIUM);
        assertThat(notificationResult.getAuthor()).isEqualTo(user);
        assertThat(notificationResult.getTarget()).isEqualTo(expectedTarget);
    }

}
