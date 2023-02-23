package de.tum.in.www1.artemis.service.notifications;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;

@Service
public class InstantNotificationService {

    public void sendNotification(Notification notification, User user, Object notificationSubject) {
    }

    public void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
    }
}
