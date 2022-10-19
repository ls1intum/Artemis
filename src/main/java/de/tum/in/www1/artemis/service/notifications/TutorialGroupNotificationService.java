package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UPDATED;
import static de.tum.in.www1.artemis.domain.notification.TutorialGroupNotificationFactory.createTutorialGroupNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.EMAIL;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.notification.TutorialGroupNotification;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupNotificationRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.in.www1.artemis.service.MailService;

@Service
public class TutorialGroupNotificationService {

    private final TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final NotificationSettingsService notificationSettingsService;

    private final MailService mailService;

    public TutorialGroupNotificationService(TutorialGroupNotificationRepository tutorialGroupNotificationRepository,
            TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, SimpMessageSendingOperations messagingTemplate,
            NotificationSettingsService notificationSettingsService, MailService mailService) {
        this.tutorialGroupNotificationRepository = tutorialGroupNotificationRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationSettingsService = notificationSettingsService;
        this.mailService = mailService;
    }

    /**
     * Notify registered students about a deleted tutorial group
     *
     * @param tutorialGroup the deleted tutorial group
     */
    public void notifyAboutTutorialGroupDeletion(TutorialGroup tutorialGroup) {
        saveAndSend(createTutorialGroupNotification(tutorialGroup, TUTORIAL_GROUP_DELETED), true);
    }

    /**
     * Notify registered students about an updated tutorial group
     *
     * @param tutorialGroup    the updated tutorial group
     * @param notifyTutor      whether the tutor should be notified about the update via email
     * @param notificationText the notification text
     */
    public void notifyAboutTutorialGroupUpdate(TutorialGroup tutorialGroup, boolean notifyTutor, String notificationText) {
        saveAndSend(createTutorialGroupNotification(tutorialGroup, TUTORIAL_GROUP_UPDATED, notificationText), notifyTutor);
    }

    private void saveAndSend(TutorialGroupNotification notification, boolean notifyTutor) {
        tutorialGroupNotificationRepository.save(notification);
        sendNotificationViaWebSocket(notification);
        sendNotificationViaMail(notification, notifyTutor);
    }

    private void sendNotificationViaMail(TutorialGroupNotification notification, boolean notifyTutor) {
        if (notificationSettingsService.checkNotificationTypeForEmailSupport(notification.notificationType)) {
            var usersToMail = findUsersToMail(notification, notifyTutor);
            if (!usersToMail.isEmpty()) {
                mailService.sendNotificationEmailForMultipleUsers(notification, new ArrayList<>(usersToMail), notification.getTutorialGroup());
            }
        }
    }

    private void sendNotificationViaWebSocket(TutorialGroupNotification notification) {
        // as we send to a general topic, we filter client side by individual notification settings
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }

    private Set<User> findUsersToMail(TutorialGroupNotification notification, boolean notifyTutor) {
        var tutorialGroup = notification.getTutorialGroup();
        // ToDo: Adapt to the type of registration in the future
        var potentiallyInterestedUsers = tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)
                .stream().map(TutorialGroupRegistration::getStudent);
        if (tutorialGroup.getTeachingAssistant() != null && notifyTutor) {
            potentiallyInterestedUsers = Stream.concat(potentiallyInterestedUsers, Stream.of(tutorialGroup.getTeachingAssistant()));
        }
        return potentiallyInterestedUsers.filter(user -> StringUtils.hasText(user.getEmail()))
                .filter(user -> notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(notification, user, EMAIL)).collect(Collectors.toSet());
    }
}
