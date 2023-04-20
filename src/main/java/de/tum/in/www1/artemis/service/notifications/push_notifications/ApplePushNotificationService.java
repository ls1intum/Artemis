package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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

    private final Logger log = LoggerFactory.getLogger(ApplePushNotificationService.class);

    @Value("${artemis.push-notification-relay:#{Optional.empty()}}")
    private Optional<String> relayServerBaseUrl;

    private final RestTemplate restTemplate;

    public ApplePushNotificationService(PushNotificationDeviceConfigurationRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        var threadPool = Executors.newFixedThreadPool(10);
        var futures = requests.stream().map(request -> CompletableFuture.runAsync(() -> sendRelayRequest(request, relayServerBaseUrl))).toList()
                .toArray(new CompletableFuture[requests.size()]);

        CompletableFuture.allOf(futures).thenApply((empty) -> {
            threadPool.shutdown();
            return null;
        });
    }

    @Async
    void sendRelayRequest(RelayNotificationRequest request, String relayServerBaseUrl) {
        RetryTemplate template = RetryTemplate.builder().exponentialBackoff(1000, 4, 60 * 1000).retryOn(RestClientException.class).maxAttempts(4).build();

        try {
            template.execute((RetryCallback<Void, RestClientException>) context -> {
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                String body = new Gson().toJson(request);
                HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);
                restTemplate.postForObject(relayServerBaseUrl + "/api/push_notification/send_apns", httpEntity, String.class);

                return null;
            });
        }
        catch (RestClientException e) {
            log.error("Could not send APNS notifications");
        }
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
}
