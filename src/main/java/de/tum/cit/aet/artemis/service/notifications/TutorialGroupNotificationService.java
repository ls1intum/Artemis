package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UPDATED;
import static de.tum.cit.aet.artemis.domain.notification.TutorialGroupNotificationFactory.createTutorialGroupNotification;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.domain.notification.TutorialGroupNotification;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.repository.tutorialgroups.TutorialGroupNotificationRepository;
import de.tum.cit.aet.artemis.repository.tutorialgroups.TutorialGroupRegistrationRepository;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;

@Profile(PROFILE_CORE)
@Service
public class TutorialGroupNotificationService {

    private final TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    private final TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final NotificationSettingsService notificationSettingsService;

    private final GeneralInstantNotificationService notificationService;

    public TutorialGroupNotificationService(TutorialGroupNotificationRepository tutorialGroupNotificationRepository,
            TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository, WebsocketMessagingService websocketMessagingService,
            NotificationSettingsService notificationSettingsService, GeneralInstantNotificationService notificationService) {
        this.tutorialGroupNotificationRepository = tutorialGroupNotificationRepository;
        this.tutorialGroupRegistrationRepository = tutorialGroupRegistrationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.notificationSettingsService = notificationSettingsService;
        this.notificationService = notificationService;
    }

    /**
     * Notify registered students about a deleted tutorial group
     *
     * @param tutorialGroup the deleted tutorial group
     */
    public void notifyAboutTutorialGroupDeletion(TutorialGroup tutorialGroup) {
        saveAndSend(createTutorialGroupNotification(TutorialGroup.preventCircularJsonConversion(tutorialGroup), TUTORIAL_GROUP_DELETED), true);
    }

    /**
     * Notify registered students about an updated tutorial group
     *
     * @param tutorialGroup    the updated tutorial group
     * @param notifyTutor      whether the tutor should be notified about the update via email
     * @param notificationText the notification text
     */
    public void notifyAboutTutorialGroupUpdate(TutorialGroup tutorialGroup, boolean notifyTutor, String notificationText) {
        saveAndSend(createTutorialGroupNotification(TutorialGroup.preventCircularJsonConversion(tutorialGroup), TUTORIAL_GROUP_UPDATED, notificationText), notifyTutor);
    }

    private void saveAndSend(TutorialGroupNotification notification, boolean notifyTutor) {
        tutorialGroupNotificationRepository.save(notification);
        sendNotificationViaWebSocket(notification);
        sendInstantNotification(notification, notifyTutor);
    }

    private void sendInstantNotification(TutorialGroupNotification notification, boolean notifyTutor) {
        if (notificationSettingsService.checkNotificationTypeForInstantNotificationSupport(notification.notificationType)) {
            var usersToMail = findUsersToNotify(notification, notifyTutor);
            if (!usersToMail.isEmpty()) {
                notificationService.sendNotification(notification, usersToMail, notification.getTutorialGroup());
            }
        }
    }

    private void sendNotificationViaWebSocket(TutorialGroupNotification notification) {
        // as we send to a general topic, we filter client side by individual notification settings
        notification.getTutorialGroup().getRegistrations().stream().map(TutorialGroupRegistration::getStudent)
                .forEach(user -> websocketMessagingService.sendMessage(notification.getTopic(user.getId()), notification));
        websocketMessagingService.sendMessage(notification.getTopic(notification.getTutorialGroup().getTeachingAssistant().getId()), notification);
    }

    private Set<User> findUsersToNotify(TutorialGroupNotification notification, boolean notifyTutor) {
        var tutorialGroup = notification.getTutorialGroup();
        // ToDo: Adapt to the type of registration in the future
        var potentiallyInterestedUsers = tutorialGroupRegistrationRepository.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)
                .stream().map(TutorialGroupRegistration::getStudent);
        if (tutorialGroup.getTeachingAssistant() != null && notifyTutor) {
            potentiallyInterestedUsers = Stream.concat(potentiallyInterestedUsers, Stream.of(tutorialGroup.getTeachingAssistant()));
        }
        return potentiallyInterestedUsers.filter(user -> StringUtils.hasText(user.getEmail())).collect(Collectors.toSet());
    }
}
