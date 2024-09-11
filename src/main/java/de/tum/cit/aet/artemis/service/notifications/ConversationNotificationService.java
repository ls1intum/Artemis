package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.communication.domain.NotificationType.CONVERSATION_NEW_MESSAGE;
import static de.tum.cit.aet.artemis.communication.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.NEW_MESSAGE_CHANNEL_TEXT;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.NEW_MESSAGE_DIRECT_TEXT;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.NEW_MESSAGE_GROUP_CHAT_TEXT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.notification.ConversationNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationPlaceholderCreator;
import de.tum.cit.aet.artemis.communication.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.SingleUserNotificationFactory;
import de.tum.cit.aet.artemis.communication.repository.SingleUserNotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationNotificationRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Service for sending notifications about new messages in conversations.
 */
@Profile(PROFILE_CORE)
@Service
public class ConversationNotificationService {

    private final ConversationNotificationRepository conversationNotificationRepository;

    private final GeneralInstantNotificationService generalInstantNotificationService;

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    public ConversationNotificationService(ConversationNotificationRepository conversationNotificationRepository,
            GeneralInstantNotificationService generalInstantNotificationService, SingleUserNotificationRepository singleUserNotificationRepository) {
        this.conversationNotificationRepository = conversationNotificationRepository;
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
        NotificationType notificationType = CONVERSATION_NEW_MESSAGE;
        String conversationName = conversation.getHumanReadableNameForReceiver(createdMessage.getAuthor());

        // add channel/groupChat/oneToOneChat string to placeholders for notification to distinguish in mobile client
        if (conversation instanceof Channel channel) {
            notificationText = NEW_MESSAGE_CHANNEL_TEXT;
            placeholders = createPlaceholdersNewMessageChannelText(course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(),
                    createdMessage.getAuthor().getName(), conversationName, "channel");
            notificationType = getNotificationTypeForChannel(channel);
        }
        else if (conversation instanceof GroupChat) {
            notificationText = NEW_MESSAGE_GROUP_CHAT_TEXT;
            placeholders = createPlaceholdersNewMessageChannelText(course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(),
                    createdMessage.getAuthor().getName(), conversationName, "groupChat");
        }
        else {
            notificationText = NEW_MESSAGE_DIRECT_TEXT;
            placeholders = createPlaceholdersNewMessageChannelText(course.getTitle(), createdMessage.getContent(), createdMessage.getCreationDate().toString(),
                    createdMessage.getAuthor().getName(), conversationName, "oneToOneChat");
        }
        ConversationNotification notification = createConversationMessageNotification(course.getId(), createdMessage, notificationType, notificationText, true, placeholders);
        save(notification, mentionedUsers, placeholders);
        return notification;
    }

    @NotificationPlaceholderCreator(values = { CONVERSATION_NEW_MESSAGE })
    public static String[] createPlaceholdersNewMessageChannelText(String courseTitle, String messageContent, String messageCreationDate, String channelName, String authorName,
            String conversationType) {
        return new String[] { courseTitle, messageContent, messageCreationDate, channelName, authorName, conversationType };
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
     */
    public void notifyAboutNewMessage(Post createdMessage, ConversationNotification notification, Set<User> recipients) {
        Post notificationSubject = new Post();
        notificationSubject.setId(createdMessage.getId());
        notificationSubject.setConversation(createdMessage.getConversation());
        notificationSubject.setContent(createdMessage.getContent());
        notificationSubject.setTitle(createdMessage.getTitle());
        notificationSubject.setAuthor(createdMessage.getAuthor());
        generalInstantNotificationService.sendNotification(notification, recipients, notificationSubject);
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
        return CONVERSATION_NEW_MESSAGE;
    }
}
