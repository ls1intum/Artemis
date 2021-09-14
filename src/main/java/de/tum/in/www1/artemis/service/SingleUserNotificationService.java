package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private MailService mailService;

    private NotificationSettingsService notificationSettingsService;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate, MailService mailService,
            NotificationSettingsService notificationSettingsService) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Notify author of a question for an exercise that there is a new answer.
     *
     * @param answer for exercise that is new
     */
    public void notifyUserAboutNewAnswerForExercise(AnswerPost answer) {
        saveAndSend(createNotification(answer, NotificationType.NEW_ANSWER_POST_FOR_EXERCISE), answer);
    }

    /**
     * Notify author of a question for a lecture that there is a new answer.
     *
     * @param answer for lecture that is new
     */
    public void notifyUserAboutNewAnswerForLecture(AnswerPost answer) {
        saveAndSend(createNotification(answer, NotificationType.NEW_ANSWER_POST_FOR_LECTURE), answer);
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an email.
     * @param notification that should be saved and sent
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject) {
        singleUserNotificationRepository.save(notification);
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
        prepareSingleUserNotificationEmail(notification, notificationSubject);
    }

    /**
     * Checks if an email should be created based on the provided notification, user, notification settings and type for SingleUserNotifications
     * If the checks are successful creates and sends a corresponding email
     * If the notification type indicates an urgent (critical) email it will be send regardless of settings
     * @param notification that should be checked
     */
    private void prepareSingleUserNotificationEmail(SingleUserNotification notification, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        boolean hasEmailSupport = notificationSettingsService.checkNotificationTypeForEmailSupport(type);
        if (hasEmailSupport) {
            boolean isAllowedBySettings = false;
            boolean isUrgentEmail = notificationSettingsService.checkNotificationTypeForEmailUrgency(type);
            if (!isUrgentEmail) {
                isAllowedBySettings = notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient());
            }
            if (isUrgentEmail || isAllowedBySettings) {
                mailService.sendNotificationEmail(notification, notification.getRecipient(), notificationSubject);
            }
        }
    }
}
