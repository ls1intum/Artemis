package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.EMAIL;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.PUSH;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.in.www1.artemis.service.notifications.push_notifications.FirebasePushNotificationService;

@Service
public class GeneralInstantNotificationService implements InstantNotificationService {

    private ApplePushNotificationService applePushNotificationService;

    private FirebasePushNotificationService firebasePushNotificationService;

    private MailService mailService;

    private NotificationSettingsService notificationSettingsService;

    private List<InstantNotificationService> instantNotificationServices;

    public GeneralInstantNotificationService(ApplePushNotificationService applePushNotificationService, FirebasePushNotificationService firebasePushNotificationService,
            MailService mailService, NotificationSettingsService notificationSettingsService) {
        this.applePushNotificationService = applePushNotificationService;
        this.firebasePushNotificationService = firebasePushNotificationService;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;

        instantNotificationServices = List.of(applePushNotificationService, firebasePushNotificationService, mailService);
    }

    /**
     * Checks for the user if the notification should be sent as an email or/and as a push notification.
     * Then delegates the actual sending to the corresponding {@link InstantNotificationService}s
     */
    @Override
    public void sendNotification(Notification notification, User user, Object notificationSubject) {
        boolean allowsEmail = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, user, EMAIL);

        boolean allowsPush = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, user, PUSH);

        if (allowsEmail) {
            mailService.sendNotification(notification, user, notificationSubject);
        }

        if (allowsPush) {
            applePushNotificationService.sendNotification(notification, user, notificationSubject);
            firebasePushNotificationService.sendNotification(notification, user, notificationSubject);
        }
    }

    /**
     * Checks for each user if the notification should be sent as an email or/and as a push notification.
     * Then delegates the actual sending to the corresponding {@link InstantNotificationService}s
     */
    @Override
    public void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
        var emailRecipients = filterRecipients(notification, users, EMAIL);
        var pushRecipients = filterRecipients(notification, users, PUSH);

        applePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);
        firebasePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);
        mailService.sendNotification(notification, emailRecipients, notificationSubject);
    }

    @NotNull
    private List<User> filterRecipients(Notification notification, List<User> users, NotificationSettingsCommunicationChannel channel) {
        return users.stream().filter(user -> notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, user, channel))
                .toList();
    }
}
