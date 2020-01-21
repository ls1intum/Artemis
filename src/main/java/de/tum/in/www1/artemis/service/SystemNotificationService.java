package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Objects;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;

@Service
public class SystemNotificationService {

    private final SimpMessageSendingOperations messagingTemplate;

    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationService(SimpMessageSendingOperations messagingTemplate, SystemNotificationRepository systemNotificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.systemNotificationRepository = systemNotificationRepository;
    }

    public SystemNotification findActiveSystemNotification() {
        List<SystemNotification> allActiveSystemNotification = systemNotificationRepository.findAllActiveSystemNotification();
        return allActiveSystemNotification.size() > 0 ? allActiveSystemNotification.get(0) : null;
    }

    public void sendNotification(SystemNotification systemNotification) {
        // we cannot send null over websockets so in case the systemNotification object is null, we send 'deleted' and handle this case in the client
        messagingTemplate.convertAndSend("/topic/system-notification", Objects.requireNonNullElse(systemNotification, "deleted"));
    }
}
