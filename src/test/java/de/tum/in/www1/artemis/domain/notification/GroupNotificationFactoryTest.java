package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

public class GroupNotificationFactoryTest {

    @Mock
    private Attachment attachment;

    @Mock
    private User user;

    @Mock
    private Lecture lecture;

    @Mock
    private Course course;

    @Mock
    private GroupNotificationType groupNotificationType;

    @Test
    public void createNotificationBasedOnAttachment() {

        GroupNotificationFactory groupNotificationFactory = mock(GroupNotificationFactory.class, CALLS_REAL_METHODS);

        // prepare method inputs
        NotificationType notificationType = NotificationType.ATTACHMENT_CHANGE;
        String notificationText = "notification text";
        // attachment =
        String attachmentName = "AttachmentName";

        when(attachment.getAttachmentUnit()).thenReturn(null);
        when(attachment.getLecture()).thenReturn(lecture);
        when(attachment.getName()).thenReturn(attachmentName);

        when(lecture.getCourse()).thenReturn(course);

        // create notification
        GroupNotification notificationResult = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);

        // check if created notification is correct
        assertThat(notificationResult.getTitle()).isEqualTo("Attachment updated");
        assertThat(notificationResult.getText()).isEqualTo(notificationText);

    }

}
