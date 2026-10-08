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
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
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

    @Mock
    private TestAccountEmailService testAccountEmailPolicy;

    @Mock
    private GlobalNotificationSettingService globalNotificationSettingService;

    private SystemNotificationService systemNotificationService;

    @BeforeEach
    void setUp() {
        systemNotificationService = new SystemNotificationService(websocketMessagingService, systemNotificationRepository, maintenanceEmailRecipientRepository, mailSendingService,
                testAccountEmailPolicy, globalNotificationSettingService);
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
    void shouldSendMaintenanceEmailToATestAccountOnlyWhenItSwitchedTheNotificationOn() {
        ZonedDateTime notificationDate = ZonedDateTime.now();
        SystemNotification notification = new SystemNotification();
        notification.setTitle("Maintenance");
        notification.setText("Artemis will be unavailable.");
        notification.setNotificationDate(notificationDate);
        notification.setExpireDate(notificationDate.plusHours(1));
        notification.setType(SystemNotificationType.WARNING);

        var optedInTestAccount = new MaintenanceEmailRecipientDTO(1L, "artemis_test_user_16", "optedin@example.com", "en", "Opted", "In");
        var silentTestAccount = new MaintenanceEmailRecipientDTO(2L, "artemis_test_user_17", "silent@example.com", "en", "Silent", "Account");
        var regularInstructor = new MaintenanceEmailRecipientDTO(3L, "ge12abc", "instructor@example.com", "de", "Regular", "Instructor");
        when(maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(any(ZonedDateTime.class)))
                .thenReturn(Set.of(optedInTestAccount, silentTestAccount, regularInstructor));
        when(testAccountEmailPolicy.isTestAccount(any())).thenAnswer(invocation -> {
            String login = invocation.getArgument(0);
            return login != null && login.startsWith("artemis_test_user_");
        });
        when(globalNotificationSettingService.isNotificationEnabled(1L, "artemis_test_user_16", GlobalNotificationType.MAINTENANCE)).thenReturn(true);
        when(globalNotificationSettingService.isNotificationEnabled(2L, "artemis_test_user_17", GlobalNotificationType.MAINTENANCE)).thenReturn(false);

        systemNotificationService.sendMaintenanceEmails(notification);

        ArgumentCaptor<MailRecipientDTO> recipients = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService, times(2)).buildAndSendAsync(recipients.capture(), eq("email.notification.maintenance.title"), eq("mail/notification/maintenanceEmail"),
                anyMap());
        // The login is part of the recipient, because it is what the mail layer uses to recognise a test account.
        assertThat(recipients.getAllValues()).extracting(MailRecipientDTO::login).containsExactlyInAnyOrder("artemis_test_user_16", "ge12abc");
    }
}
