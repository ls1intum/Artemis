package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.EMAIL;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.PUSH;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.in.www1.artemis.service.notifications.push_notifications.FirebasePushNotificationService;

/**
 * A Handler for InstantNotifications such as MailService and PushNotifications.
 * Handles the sending of Notifications via this channels.
 */
@Service
public class GeneralInstantNotificationService implements InstantNotificationService {

    @Autowired
    private final ApplePushNotificationService applePushNotificationService;

    @Autowired
    private final FirebasePushNotificationService firebasePushNotificationService;

    @Autowired
    private final MailService mailService;

    @Autowired
    private final NotificationSettingsService notificationSettingsService;

    public GeneralInstantNotificationService(ApplePushNotificationService applePushNotificationService, FirebasePushNotificationService firebasePushNotificationService,
            MailService mailService, NotificationSettingsService notificationSettingsService) {
        this.applePushNotificationService = applePushNotificationService;
        this.firebasePushNotificationService = firebasePushNotificationService;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Checks for the user if the notification should be sent as an email or/and as a push notification.
     * Then delegates the actual sending to the corresponding {@link InstantNotificationService}s
     */
    @Override
    public void sendNotification(Notification notification, User user, Object notificationSubject) {
        boolean allowsEmail = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, user, EMAIL);

        boolean allowsPush = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, user, PUSH);

        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());
        if (allowsEmail && notificationSettingsService.checkNotificationTypeForEmailSupport(type)) {
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
    @Async
    @Override
    public void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
        SecurityUtils.setAuthorizationObject();

        var emailRecipients = filterRecipients(notification, users, EMAIL);
        var pushRecipients = filterRecipients(notification, users, PUSH);

        applePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);
        firebasePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);

        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());
        if (notificationSettingsService.checkNotificationTypeForEmailSupport(type)) {
            mailService.sendNotification(notification, emailRecipients, notificationSubject);
        }
    }

    @NotNull
    private List<User> filterRecipients(Notification notification, List<User> users, NotificationSettingsCommunicationChannel channel) {
        return notificationSettingsService.filterUsersByNotificationIsAllowedInCommunicationChannelBySettings(notification, users, channel);
    }
}
