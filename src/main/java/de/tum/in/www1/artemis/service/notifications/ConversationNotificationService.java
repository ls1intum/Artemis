package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationNotificationRepository;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * Service for sending notifications about new messages in conversations.
 */
@Service
public class ConversationNotificationService {

    private final ConversationNotificationRepository conversationNotificationRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final GeneralInstantNotificationService generalInstantNotificationService;

    public ConversationNotificationService(ConversationNotificationRepository conversationNotificationRepository, WebsocketMessagingService websocketMessagingService,
            GeneralInstantNotificationService generalInstantNotificationService) {
        this.conversationNotificationRepository = conversationNotificationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.generalInstantNotificationService = generalInstantNotificationService;
    }

    /**
     * Notify registered students about new message
     *
     * @param createdMessage the new message
     * @param recipients     the users which should be notified about the new message
     * @param course         the course in which the message was posted
     */
    public void notifyAboutNewMessage(Post createdMessage, Set<User> recipients, Course course) {
        String notificationText;
        String[] placeholders;
        String conversationName = createdMessage.getConversation().getHumanReadableNameForReceiver(createdMessage.getAuthor());

        // add channel/groupChat/oneToOneChat string to placeholders for notification to distinguish in mobile client
        if (createdMessage.getConversation() instanceof Channel channel) {
            notificationText = NEW_MESSAGE_CHANNEL_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), channel.getName(),
                    createdMessage.getAuthor().getName(), "channel" };
        }
        else if (createdMessage.getConversation() instanceof GroupChat groupChat) {
            notificationText = NEW_MESSAGE_GROUP_CHAT_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), createdMessage.getAuthor().getName(),
                    conversationName, "groupChat" };
        }
        else {
            notificationText = NEW_MESSAGE_DIRECT_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), createdMessage.getAuthor().getName(),
                    conversationName, "oneToOneChat" };
        }
        var notification = createConversationMessageNotification(course.getId(), createdMessage, NotificationType.CONVERSATION_NEW_MESSAGE, notificationText, true, placeholders);
        saveAndSend(notification, recipients);
    }

    private void saveAndSend(ConversationNotification notification, Set<User> recipients) {
        conversationNotificationRepository.save(notification);
        sendNotificationViaWebSocket(notification, recipients);

        generalInstantNotificationService.sendNotification(notification, recipients, null);
    }

    private void sendNotificationViaWebSocket(ConversationNotification notification, Set<User> recipients) {
        recipients.forEach(user -> websocketMessagingService.sendMessage(notification.getTopic(user.getId()), notification));
    }
}
