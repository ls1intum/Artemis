package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.concurrent.Executor;

import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.notification.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of Android Notifications to the Relay Service
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FirebasePushNotificationService extends PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:https://hermes-staging.artemis.cit.tum.de}")
    private String relayServerBaseUrl;

    public FirebasePushNotificationService(PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository, RestTemplate restTemplate,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        super(restTemplate, taskExecutor);
        repository = pushNotificationDeviceConfigurationRepository;
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayBaseUrl) {
        // The relay server accepts at most 500 messages per batch. Dispatch on the application task executor rather
        // than the common ForkJoinPool, for the reason given on the overridden method.
        ListUtils.partition(requests, 500).forEach(batch -> taskExecutor.execute(() -> sendSpecificNotificationRequestsToEndpoint(batch, relayBaseUrl)));
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
        catch (JacksonException e) {
            log.error("Failed to send push notification to relay server", e);
        }
    }
}
