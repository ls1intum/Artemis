package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsCommunicationChannel.EMAIL;
import static de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsCommunicationChannel.PUSH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A Handler for InstantNotifications such as MailService and PushNotifications.
 * Handles the sending of Notifications via these channels.
 */
@Profile(PROFILE_CORE)
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
    @Override
    public void sendNotification(Notification notification, Set<User> users, Object notificationSubject) {
        var emailRecipients = filterRecipients(notification, users, EMAIL);
        var pushRecipients = filterRecipients(notification, users, PUSH);

        applePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);
        firebasePushNotificationService.sendNotification(notification, pushRecipients, notificationSubject);

        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());
        if (notificationSettingsService.checkNotificationTypeForEmailSupport(type)) {
            // Add the author of the post to the email recipients, if they have email notifications enabled for the notification type
            if (notificationSubject instanceof Post post && post.getConversation() != null && !filterRecipients(notification, Set.of(post.getAuthor()), EMAIL).isEmpty()) {
                emailRecipients.add(post.getAuthor());
            }

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
