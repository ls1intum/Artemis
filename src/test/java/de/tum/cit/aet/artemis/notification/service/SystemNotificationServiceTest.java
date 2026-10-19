package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.notification.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.MaintenanceEmailRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationDTO;
import de.tum.cit.aet.artemis.notification.repository.MaintenanceEmailRecipientRepository;
import de.tum.cit.aet.artemis.notification.repository.SystemNotificationRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

@ExtendWith(MockitoExtension.class)
class SystemNotificationServiceTest {

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @Mock
    private SystemNotificationRepository systemNotificationRepository;

    @Mock
    private MaintenanceEmailRecipientRepository maintenanceEmailRecipientRepository;

    @Mock
    private MailSendingService mailSendingService;

    private SystemNotificationService systemNotificationService;

    @BeforeEach
    void setUp() {
        systemNotificationService = new SystemNotificationService(websocketMessagingService, systemNotificationRepository, maintenanceEmailRecipientRepository, mailSendingService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSendSystemNotificationDtosToTheNotificationTopicOnly() {
        ZonedDateTime notificationDate = ZonedDateTime.now();
        SystemNotification notification = new SystemNotification();
        notification.setId(1L);
        notification.setTitle("Maintenance");
        notification.setText("Artemis will be unavailable.");
        notification.setNotificationDate(notificationDate);
        notification.setExpireDate(notificationDate.plusHours(1));
        notification.setType(SystemNotificationType.WARNING);
        when(systemNotificationRepository.findAllActiveAndFutureSystemNotifications(any(ZonedDateTime.class))).thenReturn(List.of(notification));

        systemNotificationService.distributeActiveAndFutureNotificationsToClients();

        List<SystemNotificationDTO> expectedNotifications = List.of(SystemNotificationDTO.from(notification));
        verify(websocketMessagingService).sendMessage(topic("/topic/notification/system-notification"), eq(expectedNotifications));
        // The retired /topic/system-notification mirror must not come back: one send, not two.
        verifyNoMoreInteractions(websocketMessagingService);
    }

    @Test
    void shouldSendMaintenanceEmailToEveryRecipientTheQueryReturnsAndCarryTheTestUserFlag() {
        ZonedDateTime notificationDate = ZonedDateTime.now();
        SystemNotification notification = new SystemNotification();
        notification.setTitle("Maintenance");
        notification.setText("Artemis will be unavailable.");
        notification.setNotificationDate(notificationDate);
        notification.setExpireDate(notificationDate.plusHours(1));
        notification.setType(SystemNotificationType.WARNING);

        // Who is eligible, including that a test user must have switched the notification on, is decided by the query (see the repository
        // tests), the same one that the preview count uses, so every recipient it returns gets the mail.
        var optedInTestAccount = new MaintenanceEmailRecipientDTO(1L, "test_user_16", "optedin@example.com", "en", "Opted", "In", true);
        var regularInstructor = new MaintenanceEmailRecipientDTO(3L, "ge12abc", "instructor@example.com", "de", "Regular", "Instructor", false);
        when(maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(any(ZonedDateTime.class))).thenReturn(Set.of(optedInTestAccount, regularInstructor));

        systemNotificationService.sendMaintenanceEmails(notification);

        ArgumentCaptor<MailRecipientDTO> recipients = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService, times(2)).buildAndSendAsync(recipients.capture(), eq("email.notification.maintenance.title"), eq("mail/notification/maintenanceEmail"),
                anyMap());
        assertThat(recipients.getAllValues()).extracting(MailRecipientDTO::login).containsExactlyInAnyOrder("test_user_16", "ge12abc");
        // The flag is part of the recipient, because the mail layer needs it for the mails that have no setting of their own.
        assertThat(recipients.getAllValues()).filteredOn(recipient -> "test_user_16".equals(recipient.login())).singleElement().extracting(MailRecipientDTO::testUser)
                .isEqualTo(true);
    }
}
