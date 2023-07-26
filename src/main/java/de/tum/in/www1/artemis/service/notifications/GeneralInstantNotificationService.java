package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.EMAIL;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.PUSH;

import java.util.Set;

import javax.validation.constraints.NotNull;

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
 * Handles the sending of Notifications via these channels.
 */
@Service
public class GeneralInstantNotificationService implements InstantNotificationService {

    private final ApplePushNotificationService applePushNotificationService;

    private final FirebasePushNotificationService firebasePushNotificationService;

    private final MailService mailService;

    private final NotificationSettingsService notificationSettingsService;

    /**
     * Constructor to create a GeneralInstantNotificationService
     */
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
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param user                who should be contacted
     * @param notificationSubject that is used to provide further information for mails (e.g. exercise, attachment, post, etc.)
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
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param users               who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    @Async
    @Override
    public void sendNotification(Notification notification, Set<User> users, Object notificationSubject) {
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

    /**
     * Filters the given user array based on if the notification (i.e. its type based on title) is allowed by the respective notification settings
     *
     * @param notification which type (based on title) should be checked
     * @param users        whose notification settings will be used for checking
     * @param channel      which channel to use (e.g. email or webapp or push)
     * @return filtered user list
     */
    @NotNull
    private Set<User> filterRecipients(Notification notification, Set<User> users, NotificationSettingsCommunicationChannel channel) {
        return notificationSettingsService.filterUsersByNotificationIsAllowedInCommunicationChannelBySettings(notification, users, channel);
    }
}
