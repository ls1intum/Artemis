package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.Message;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

@Service
public class FirebasePushNotificationService extends PushNotificationService {

    private final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    @Value("${artemis.push-notification.firebase.path}")
    private Optional<String> credentialsPath;

    public FirebasePushNotificationService() {
        if (credentialsPath.isPresent()) {
            try {
                final var credentials = GoogleCredentials.fromStream(Files.newInputStream(Path.of(credentialsPath.get())));

                final FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();

                FirebaseApp.initializeApp(options);
            }
            catch (IOException e) {
                log.error("Exception while loading Firebase credentials", e);
            }
        }
    }

    public void scheduleSendMessage(Message message) {
        // RetryTemplate template = RetryTemplate.builder()
        // .exponentialBackoff(1000, 2, 60 * 1000)
        // .retryOn(FirebaseMessagingException.class)
        // .build();
        //
        // template.execute(() -> {
        // FirebaseMessaging
        // .getInstance()
        // .send(Message.builder().build())
        // });
        //
        // RetryTemplate
        // RetryTemplateFactory factory = new RetryTemplate();
        // factory.
        // ExponentialBackOff backOff = new ExponentialBackOff(1000, 2);
        // try {
        // FirebaseMessaging
        // .getInstance()
        // .send(Message.builder().build())
        // }

    }

    @Override
    void sendPushNotification(String title, String body, String target, NotificationType notificationType, List<User> users) {

    }

    @Override
    void sendPushNotification(String title, String body, String target, NotificationType notificationType, User user) {

    }
}
