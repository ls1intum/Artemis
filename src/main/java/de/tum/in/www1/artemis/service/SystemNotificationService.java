package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;

@Service
public class SystemNotificationService {

    private final SimpMessageSendingOperations messagingTemplate;

    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationService(SimpMessageSendingOperations messagingTemplate, SystemNotificationRepository systemNotificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.systemNotificationRepository = systemNotificationRepository;
    }

    public SystemNotification findActiveSystemNotification() {
        // The 'user' does not need to be logged into Artemis, this leads to an issue when accessing custom repository methods. Therefore a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        List<SystemNotification> allActiveSystemNotification = systemNotificationRepository.findAllActiveSystemNotification(ZonedDateTime.now());
        return allActiveSystemNotification.isEmpty() ? null : allActiveSystemNotification.get(0);
    }

    public void sendNotification(SystemNotification systemNotification) {
        // we cannot send null over websockets so in case the systemNotification object is null, we send 'deleted' and handle this case in the client
        messagingTemplate.convertAndSend("/topic/system-notification", Objects.requireNonNullElse(systemNotification, "deleted"));
    }
}
