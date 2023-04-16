package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
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
     * @param createdMessage the new message
     */
    public void notifyAboutNewMessage(Post createdMessage) {

        String notificationText = "";
        String[] placeholders = {};
        if (createdMessage.getConversation() instanceof Channel channel) {
            // notificationText = "New message in " + channel.getName() + " channel from " + createdMessage.getAuthor().getName() + " in course (" + channel.getCourse().getTitle()
            // + ")";
            notificationText = NEW_MESSAGE_CHANNEL_TEXT;
            placeholders = new String[] { channel.getName(), createdMessage.getAuthor().getName(), channel.getCourse().getTitle() };
        }
        else if (createdMessage.getConversation() instanceof GroupChat groupChat) {
            // notificationText = "New message in group chat from " + createdMessage.getAuthor().getName() + " in course (" + groupChat.getCourse().getTitle() + ")";
            notificationText = NEW_MESSAGE_GROUP_CHAT_TEXT;
            placeholders = new String[] { createdMessage.getAuthor().getName(), groupChat.getCourse().getTitle() };
        }
        else {
            // notificationText = "New direct message from " + createdMessage.getAuthor().getName() + " in course (" + createdMessage.getConversation().getCourse().getTitle() +
            // ")";
            notificationText = NEW_MESSAGE_DIRECT_TEXT;
            placeholders = new String[] { createdMessage.getAuthor().getName(), createdMessage.getConversation().getCourse().getTitle() };
        }
        saveAndSend(createConversationMessageNotification(createdMessage, NotificationType.CONVERSATION_NEW_MESSAGE, notificationText, true, placeholders));
    }

    private void saveAndSend(ConversationNotification notification) {
        conversationNotificationRepository.save(notification);
        sendNotificationViaWebSocket(notification);
    }

    private void sendNotificationViaWebSocket(ConversationNotification notification) {
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }
}
