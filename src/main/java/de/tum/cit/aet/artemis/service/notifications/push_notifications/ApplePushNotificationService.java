package de.tum.cit.aet.artemis.service.notifications.push_notifications;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of iOS Notifications to the Relay Service
 */
@Profile(PROFILE_CORE)
@Service
@EnableAsync(proxyTargetClass = true)
public class ApplePushNotificationService extends PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ApplePushNotificationService.class);

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:#{null}}")
    private Optional<String> relayServerBaseUrl;

    public ApplePushNotificationService(PushNotificationDeviceConfigurationRepository repository, RestTemplate restTemplate) {
        super(restTemplate);
        this.repository = repository;
    }

    @Override
    protected PushNotificationDeviceConfigurationRepository getRepository() {
        return repository;
    }

    @Override
    PushNotificationDeviceType getDeviceType() {
        return PushNotificationDeviceType.APNS;
    }

    @Override
    Optional<String> getRelayBaseUrl() {
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
