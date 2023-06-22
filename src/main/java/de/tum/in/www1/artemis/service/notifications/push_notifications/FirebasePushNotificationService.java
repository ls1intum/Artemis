package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.common.collect.Lists;
import com.google.gson.Gson;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of Android Notifications to the Relay Service
 */
@Service
@EnableAsync(proxyTargetClass = true)
public class FirebasePushNotificationService extends PushNotificationService {

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:#{null}}")
    private Optional<String> relayServerBaseUrl;

    public FirebasePushNotificationService(PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository, RestTemplate restTemplate) {
        super(restTemplate);

        repository = pushNotificationDeviceConfigurationRepository;
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayBaseUrl) {
        // The relay server accepts at most 500 messages per batch
        List<List<RelayNotificationRequest>> batches = Lists.partition(requests, 500);
        var futures = batches.stream().map(batch -> CompletableFuture.runAsync(() -> sendSpecificNotificationRequestsToEndpoint(batch, relayBaseUrl))).toList()
                .toArray(new CompletableFuture[0]);

        CompletableFuture.allOf(futures);
    }

    @Override
    protected PushNotificationDeviceConfigurationRepository getRepository() {
        return repository;
    }

    @Override
    PushNotificationDeviceType getDeviceType() {
        return PushNotificationDeviceType.FIREBASE;
    }

    @Override
    Optional<String> getRelayBaseUrl() {
        return relayServerBaseUrl;
    }

    @Override
    String getRelayPath() {
        return "/api/push_notification/send_firebase";
    }

    @Override
    void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        String body = new Gson().toJson(new FirebaseRelayNotificationRequests(requests));
        sendRelayRequest(body, relayServerBaseUrl);
    }
}
