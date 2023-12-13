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
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
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
     * @param conversation   the conversation the message belongs to
     * @param mentionedUsers users mentioned in the message
     * @param course         the course in which the message was posted
     * @return the created notification
     */
    public ConversationNotification createNotification(Post createdMessage, Conversation conversation, Course course, Set<User> mentionedUsers) {
        String notificationText;
        String[] placeholders;
        NotificationType notificationType = NotificationType.CONVERSATION_NEW_MESSAGE;
        String conversationName = createdMessage.getConversation().getHumanReadableNameForReceiver(createdMessage.getAuthor());

        // add channel/groupChat/oneToOneChat string to placeholders for notification to distinguish in mobile client
        if (conversation instanceof Channel channel) {
            notificationText = NEW_MESSAGE_CHANNEL_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), channel.getName(),
                    createdMessage.getAuthor().getName(), "channel" };
            notificationType = getNotificationTypeForChannel(channel);
        }
        else if (conversation instanceof GroupChat) {
            notificationText = NEW_MESSAGE_GROUP_CHAT_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), createdMessage.getAuthor().getName(),
                    conversationName, "groupChat" };
        }
        else {
            notificationText = NEW_MESSAGE_DIRECT_TEXT;
            placeholders = new String[] { course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(), createdMessage.getAuthor().getName(),
                    conversationName, "oneToOneChat" };
        }
        ConversationNotification notification = createConversationMessageNotification(course.getId(), createdMessage, notificationType, notificationText, true, placeholders);
        save(notification, mentionedUsers, placeholders);
        return notification;
    }

    private void save(ConversationNotification notification, Set<User> mentionedUsers, String[] placeHolders) {
        conversationNotificationRepository.save(notification);

        Set<SingleUserNotification> mentionedUserNotifications = mentionedUsers.stream().map(mentionedUser -> SingleUserNotificationFactory
                .createNotification(notification.getMessage(), NotificationType.CONVERSATION_USER_MENTIONED, notification.getText(), placeHolders, mentionedUser))
                .collect(Collectors.toSet());
        singleUserNotificationRepository.saveAll(mentionedUserNotifications);
    }

    /**
     * Sends push end email notifications to the provided recipients
     *
     * @param createdMessage the new message in a conversation
     * @param notification   the notification to send
     * @param recipients     the set of recipients for the notifcation
     * @param course         the course of the new message
     */
    public void notifyAboutNewMessage(Post createdMessage, ConversationNotification notification, Set<User> recipients, Course course) {
        Post notificationSubject = new Post();
        notificationSubject.setId(createdMessage.getId());
        notificationSubject.setConversation(createdMessage.getConversation());
        notificationSubject.setContent(createdMessage.getContent());
        notificationSubject.setTitle(createdMessage.getTitle());
        notificationSubject.setCourse(course);
        notificationSubject.setAuthor(createdMessage.getAuthor());
        generalInstantNotificationService.sendNotification(notification, recipients, notificationSubject);
    }

    private void sendNotificationViaWebSocket(ConversationNotification notification, Set<User> recipients) {
        recipients.forEach(user -> websocketMessagingService.sendMessage(notification.getTopic(user.getId()), notification));
    }

    /**
     * Determines the notification type for the new message based on the channel properties
     *
     * @param channel the channel the message belongs to
     * @return the notification type for the message
     */
    private static NotificationType getNotificationTypeForChannel(Channel channel) {
        if (channel.getIsAnnouncementChannel()) {
            return NotificationType.NEW_ANNOUNCEMENT_POST;
        }
        else if (channel.getLecture() != null) {
            return NotificationType.NEW_LECTURE_POST;
        }
        else if (channel.getExercise() != null) {
            return NotificationType.NEW_EXERCISE_POST;
        }
        else if (channel.getExam() != null) {
            return NotificationType.NEW_EXAM_POST;
        }
        else if (channel.getIsCourseWide()) {
            return NotificationType.NEW_COURSE_POST;
        }
        return NotificationType.CONVERSATION_NEW_MESSAGE;
    }
}
