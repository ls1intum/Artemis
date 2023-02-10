package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.service.notifications.InstantNotificationService;

public abstract class PushNotificationService implements InstantNotificationService {

    abstract void sendPushNotification(String title, String body, String target, NotificationType notificationType, User user);

    abstract void sendPushNotification(String title, String body, String target, NotificationType notificationType, List<User> users);

    @Override
    public final void sendNotification(Notification notification, User user, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        sendPushNotification(notification.getTitle(), notification.getText(), notification.getTarget(), type, user);
    }

    @Override
    public final void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        sendPushNotification(notification.getTitle(), notification.getText(), notification.getTarget(), type, users);
    }
}
