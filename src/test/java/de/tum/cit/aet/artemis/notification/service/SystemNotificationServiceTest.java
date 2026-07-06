package de.tum.cit.aet.artemis.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.notification.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
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
        systemNotificationService = new SystemNotificationService(websocketMessagingService, systemNotificationRepository, maintenanceEmailRecipientRepository,
                mailSendingService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings({ "deprecation", "removal" })
    void shouldSendSystemNotificationDtosToCurrentAndLegacyTopics() {
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
        verify(websocketMessagingService).sendMessage(SystemNotificationService.SYSTEM_NOTIFICATION_TOPIC, expectedNotifications);
        verify(websocketMessagingService).sendMessage(SystemNotificationService.LEGACY_SYSTEM_NOTIFICATION_TOPIC, expectedNotifications);
    }
}
