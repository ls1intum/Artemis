package de.tum.in.www1.artemis.service.notifications;

import java.util.List;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;

public interface InstantNotificationService {

    void sendNotification(Notification notification, User user, Object notificationSubject);

    void sendNotification(Notification notification, List<User> users, Object notificationSubject);
}
