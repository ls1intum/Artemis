package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SystemNotificationService {
    private final SimpMessageSendingOperations messagingTemplate;
    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationService(SimpMessageSendingOperations messagingTemplate, SystemNotificationRepository systemNotificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.systemNotificationRepository = systemNotificationRepository;
    }

    @Transactional(readOnly = true)
    public SystemNotification findActiveSystemNotification() {
        List<SystemNotification> allActiveSystemNotification = systemNotificationRepository.findAllActiveSystemNotification();
        return allActiveSystemNotification.size() > 0 ? allActiveSystemNotification.get(0) : null;
    }

    public void sendNotification(SystemNotification systemNotification) {
        messagingTemplate.convertAndSend("/topic/system-notification", systemNotification);
    }
}
