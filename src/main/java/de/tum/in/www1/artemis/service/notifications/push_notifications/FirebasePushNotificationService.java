package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;

@Service
public class FirebasePushNotificationService extends PushNotificationService<Message> {

    private final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    @Value("${artemis.push-notification.firebase.path}")
    private Optional<String> credentialsPath;

    private Optional<FirebaseApp> firebaseApp = Optional.empty();

    public FirebasePushNotificationService(PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository) {
        super(pushNotificationDeviceConfigurationRepository);

        if (credentialsPath.isPresent()) {
            try {
                final var credentials = GoogleCredentials.fromStream(Files.newInputStream(Path.of(credentialsPath.get())));

                final FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();

                firebaseApp = Optional.of(FirebaseApp.initializeApp(options));
            }
            catch (IOException e) {
                log.error("Exception while loading Firebase credentials", e);
            }
        }
    }

    @Override
    Message buildSendRequest(String initializationVector, String payloadCiphertext, String token) {
        return Message.builder().putData("payload", payloadCiphertext).putData("iv", initializationVector).setToken(token).build();
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<Message> messages) {
        // If the firebase app is not present, we do not have to do anything
        if (firebaseApp.isPresent()) {
            // A maximum of 500 requests can be sent as per firebase specification.
            List<List<Message>> batches = Lists.partition(messages, 500);
            for (List<Message> batch : batches) {
                scheduleSendBatch(batch, firebaseApp.get());
            }
        }
    }

    @Async
    void scheduleSendBatch(List<Message> batch, FirebaseApp firebaseApp) {
        RetryTemplate template = RetryTemplate.builder().exponentialBackoff(1000, 2, 60 * 1000).retryOn(FirebaseMessagingException.class).maxAttempts(40).build();

        try {
            template.execute((RetryCallback<Void, FirebaseMessagingException>) context -> {
                FirebaseMessaging.getInstance(firebaseApp).sendAll(batch);

                return null;
            });
        }
        catch (FirebaseMessagingException e) {
            log.error("Could not send FIREBASE notifications", e);
        }
    }
}
