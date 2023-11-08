package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
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

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    public ConversationNotificationService(ConversationNotificationRepository conversationNotificationRepository, WebsocketMessagingService websocketMessagingService,
            GeneralInstantNotificationService generalInstantNotificationService, SingleUserNotificationRepository singleUserNotificationRepository) {
        this.conversationNotificationRepository = conversationNotificationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.generalInstantNotificationService = generalInstantNotificationService;
        this.singleUserNotificationRepository = singleUserNotificationRepository;
    }

    /**
     * Notify registered students about new message
     *
     * @param createdMessage the new message
     * @param recipients     the users which should be notified about the new message
     * @param mentionedUsers users mentioned in the message
     * @param course         the course in which the message was posted
     */
    public void notifyAboutNewMessage(Post createdMessage, Set<User> recipients, Course course, Set<User> mentionedUsers) {
        String notificationText;
        String[] placeholders;
        NotificationType notificationType = NotificationType.CONVERSATION_NEW_MESSAGE;
        String conversationName = createdMessage.getConversation().getHumanReadableNameForReceiver(createdMessage.getAuthor());

        // add channel/groupChat/oneToOneChat string to placeholders for notification to distinguish in mobile client
        if (createdMessage.getConversation() instanceof Channel channel) {
            notificationText = NEW_MESSAGE_CHANNEL_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), channel.getName(),
                    createdMessage.getAuthor().getName(), "channel" };
            notificationType = NotificationType.NEW_ANNOUNCEMENT_POST;
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
        var notification = createConversationMessageNotification(course.getId(), createdMessage, notificationType, notificationText, true, placeholders);
        saveAndSend(notification, createdMessage, course, recipients, mentionedUsers, placeholders);
    }

    private void saveAndSend(ConversationNotification notification, Post createdMessage, Course course, Set<User> recipients, Set<User> mentionedUsers, String[] placeHolders) {
        conversationNotificationRepository.save(notification);

        Set<SingleUserNotification> mentionedUserNotifications = mentionedUsers.stream().map(mentionedUser -> SingleUserNotificationFactory
                .createNotification(notification.getMessage(), NotificationType.CONVERSATION_USER_MENTIONED, notification.getText(), placeHolders, mentionedUser))
                .collect(Collectors.toSet());
        singleUserNotificationRepository.saveAll(mentionedUserNotifications);
        mentionedUserNotifications.forEach(singleUserNotification -> websocketMessagingService.sendMessage(singleUserNotification.getTopic(), singleUserNotification));

        sendNotificationViaWebSocket(notification, recipients.stream().filter(recipient -> !mentionedUsers.contains(recipient)).collect(Collectors.toSet()));

        Post notificationSubject = new Post();
        notificationSubject.setId(createdMessage.getId());
        notificationSubject.setContent(createdMessage.getContent());
        notificationSubject.setCourse(course);
        generalInstantNotificationService.sendNotification(notification, recipients, notificationSubject);
    }

    private void sendNotificationViaWebSocket(ConversationNotification notification, Set<User> recipients) {
        recipients.forEach(user -> websocketMessagingService.sendMessage(notification.getTopic(user.getId()), notification));
    }
}
