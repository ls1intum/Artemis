package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.GroupNotification;
import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SingleUserNotification;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SingleUserNotificationService {
    private SingleUserNotificationRepository singleUserNotificationRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private void saveAndSendGroupNotification(SingleUserNotification userNotification) {
        singleUserNotificationRepository.save(userNotification);
        String userTopic = "topic/user/" + userNotification.getRecipient().getId();
        messagingTemplate.convertAndSend(userTopic, userNotification);
    }


    public List<Notification> findAllNewNotificationsForCurrentUser() {
        return this.singleUserNotificationRepository.findAllNewNotificationsForCurrentUser();
    }

}
