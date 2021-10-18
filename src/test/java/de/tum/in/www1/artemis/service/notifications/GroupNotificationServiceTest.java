package de.tum.in.www1.artemis.service.notifications;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.NotificationSettingsService;

public class GroupNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private static GroupNotificationService groupNotificationService;

    @Autowired
    private static GroupNotificationRepository groupNotificationRepository;

    @Autowired
    private static SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private static MailService mailService;

    @Autowired
    private static NotificationSettingsService notificationSettingsService;

    private static User student1;

    @Mock
    private static Attachment attachment;

    private String notificationText = "";

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        groupNotificationService = new GroupNotificationService(groupNotificationRepository, messagingTemplate, userRepository, mailService, notificationSettingsService);

        student1 = new User();
        student1.setId(555L);

        attachment = mock(Attachment.class);

    }

    // Attachment Change

    /**
     * Tests the method notifyStudentGroupAboutAttachmentChange
     * Should call saveAndSend when the release date is not null or not in the future
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_correctReleaseDate() {
        when(attachment.getReleaseDate()).thenReturn(ZonedDateTime.now());

        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, notificationText);

        verify(groupNotificationRepository, times(1)).save(any());

    }

}
