package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.ConversationNotificationFactory.createConversationMessageNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationNotificationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;

@Service
public class ConversationNotificationService {

    private final ConversationNotificationRepository conversationNotificationRepository;

    private final ConversationRepository conversationRepository;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final NotificationSettingsService notificationSettingsService;

    private final UserRepository userRepository;

    public ConversationNotificationService(UserRepository userRepository, ConversationNotificationRepository conversationNotificationRepository,
            ConversationMessageRepository conversationMessageRepository, SimpMessageSendingOperations messagingTemplate, NotificationSettingsService notificationSettingsService,
            ConversationParticipantRepository conversationParticipantRepository, ConversationRepository conversationRepository) {
        this.conversationNotificationRepository = conversationNotificationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationSettingsService = notificationSettingsService;
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.userRepository = userRepository;
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
        // as we send to a general topic, we filter client side by individual notification settings
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }
}
