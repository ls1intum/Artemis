package de.tum.cit.aet.artemis.communication.service.notifications.push_notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of Android Notifications to the Relay Service
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
@EnableAsync(proxyTargetClass = true)
public class FirebasePushNotificationService extends PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:https://hermes-sandbox.artemis.cit.tum.de}")
    private String relayServerBaseUrl;

    public FirebasePushNotificationService(PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository, RestTemplate restTemplate) {
        super(restTemplate);
        repository = pushNotificationDeviceConfigurationRepository;
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayBaseUrl) {
        // The relay server accepts at most 500 messages per batch
        var futures = ListUtils.partition(requests, 500).stream().map(batch -> CompletableFuture.runAsync(() -> sendSpecificNotificationRequestsToEndpoint(batch, relayBaseUrl)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures);
    }

    @Override
    public PushNotificationDeviceConfigurationRepository getRepository() {
        return repository;
    }

    @Override
    public PushNotificationDeviceType getDeviceType() {
        return PushNotificationDeviceType.FIREBASE;
    }

    @Override
    String getRelayBaseUrl() {
        return relayServerBaseUrl;
    }

    @Override
    String getRelayPath() {
        return "/api/push_notification/send_firebase";
    }

    @Override
    void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        try {
            final String body = mapper.writeValueAsString(new FirebaseRelayNotificationRequests(requests));
            sendRelayRequest(body, relayServerBaseUrl);
        }
        catch (JsonProcessingException e) {
            log.error("Failed to send push notification to relay server", e);
        }
    }
}
