package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import de.tum.in.www1.artemis.config.RestTemplateConfiguration;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;

@Service
public class ApplePushNotificationService extends PushNotificationService<ApplePushNotificationRequest> {

    @Value("${artemis.push-notification.apns.token}")
    private Optional<String> apnsToken;

    @Value("${artemis.push-notification.apns.url}")
    private Optional<String> apnsUrl;

    private final Logger log = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    public ApplePushNotificationService(PushNotificationDeviceConfigurationRepository repository) {
        super(repository);
    }

    @Override
    public ApplePushNotificationRequest buildSendRequest(String initializationVector, String payloadCiphertext, String token) {
        return new ApplePushNotificationRequest(initializationVector, payloadCiphertext, token);
    }

    @Override
    void sendNotificationRequestsToEndpoint(List<ApplePushNotificationRequest> requests) {
        if (apnsToken.isPresent() && apnsUrl.isPresent()) {
            RestTemplateConfiguration restTemplateConfiguration = new RestTemplateConfiguration();
            RestTemplate restTemplate = restTemplateConfiguration.restTemplate();

            for (ApplePushNotificationRequest request : requests) {
                sendApnsRequest(restTemplate, request);
            }
        }
    }

    @Async
    void sendApnsRequest(RestTemplate restTemplate, ApplePushNotificationRequest request) {
        RetryTemplate template = RetryTemplate.builder().exponentialBackoff(1000, 2, 60 * 1000).retryOn(RestClientException.class).maxAttempts(40).build();

        try {
            template.execute((RetryCallback<Void, RestClientException>) context -> {
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.add("path", "/3/device/" + request.token());
                httpHeaders.setBearerAuth(apnsToken.get());
                httpHeaders.add("apns-push-type", "alert");

                HttpEntity<String> httpEntity = new HttpEntity<>(request.getApnsBody(), httpHeaders);
                restTemplate.postForObject(apnsUrl.get(), httpEntity, String.class);

                return null;
            });
        }
        catch (RestClientException e) {
            log.error("Could not send APNS notifications", e);
        }
    }
}

record ApplePushNotificationRequest(String initializationVector, String payloadCiphertext, String token) {

    String getApnsBody() {
        return new Gson().toJson(new ApnsBody(new ApsBody(1), payloadCiphertext, initializationVector));
    }

    private record ApnsBody(@SerializedName("aps") ApsBody apsBody, @SerializedName("payload") String payload, @SerializedName("iv") String iv) {
    }

    private record ApsBody(@SerializedName("content-available") int contentAvailable) {
    }

}
