package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.notification.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of iOS Notifications to the Relay Service
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ApplePushNotificationService extends PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ApplePushNotificationService.class);

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:https://hermes-staging.artemis.cit.tum.de}")
    private String relayServerBaseUrl;

    public ApplePushNotificationService(PushNotificationDeviceConfigurationRepository repository, RestTemplate restTemplate, @Qualifier("taskExecutor") Executor taskExecutor) {
        super(restTemplate, taskExecutor);
        this.repository = repository;
    }

    @Override
    public PushNotificationDeviceConfigurationRepository getRepository() {
        return repository;
    }

    @Override
    public PushNotificationDeviceType getDeviceType() {
        return PushNotificationDeviceType.APNS;
    }

    @Override
    String getRelayBaseUrl() {
        return relayServerBaseUrl;
    }

    @Override
    String getRelayPath() {
        return "/api/push_notification/send_apns";
    }

    @Override
    void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        requests.forEach(request -> {
            try {
                String body = mapper.writeValueAsString(request);
                sendRelayRequest(body, relayServerBaseUrl);
            }
            catch (Exception e) {
                log.error("Failed to send push notification to relay server", e);
            }
        });
    }
}
