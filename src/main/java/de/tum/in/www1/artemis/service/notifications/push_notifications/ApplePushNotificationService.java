package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;

/**
 * Handles the sending of iOS Notifications to the Relay Service
 */
@Service
@EnableAsync(proxyTargetClass = true)
public class ApplePushNotificationService extends PushNotificationService {

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
            String body = new Gson().toJson(request);
            sendRelayRequest(body, relayServerBaseUrl);
        });
    }
}
