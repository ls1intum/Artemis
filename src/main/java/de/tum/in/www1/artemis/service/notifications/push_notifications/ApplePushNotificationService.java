package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

@Service
public class ApplePushNotificationService extends PushNotificationService {

    @Override
    void sendPushNotification(String title, String body, String target, NotificationType notificationType, User user) {

    }

    @Override
    void sendPushNotification(String title, String body, String target, NotificationType notificationType, List<User> users) {

    }
}
