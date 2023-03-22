package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationNotificationRepository;

/**
 * Service for sending notifications about new messages in conversations.
 */
@Service
public class ConversationNotificationService {

    private final ConversationNotificationRepository conversationNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ConversationNotificationService(ConversationNotificationRepository conversationNotificationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.conversationNotificationRepository = conversationNotificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Notify registered students about new message
     *
     * @param message          the new message
     * @param notificationText the notification text
     */
    public void notifyAboutNewMessage(Post message, String notificationText) {
        saveAndSend(createConversationMessageNotification(message, NotificationType.CONVERSATION_NEW_MESSAGE, notificationText));
    }

    private void saveAndSend(ConversationNotification notification) {
        conversationNotificationRepository.save(notification);
        sendNotificationViaWebSocket(notification);
    }

    private void sendNotificationViaWebSocket(ConversationNotification notification) {
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }
}
