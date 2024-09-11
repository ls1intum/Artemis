package de.tum.cit.aet.artemis.service.notifications;

import java.util.Set;

import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * The Interface which should be used for InstantNotifications such as Mails and PushNotifications.
 * The implementing classes should handle the sending of Notifications via these channels.
 */
public interface InstantNotificationService {

    /**
     * Handles the sending of the notification to the one given user
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param user                who should be contacted
     * @param notificationSubject that is used to provide further information for mails (e.g. exercise, attachment, post, etc.)
     */
    void sendNotification(Notification notification, User user, Object notificationSubject);

    /**
     * Handles the sending of the notification to the given list of users
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param users               who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    void sendNotification(Notification notification, Set<User> users, Object notificationSubject);
}
