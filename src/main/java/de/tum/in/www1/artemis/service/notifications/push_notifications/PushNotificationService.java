package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.service.notifications.InstantNotificationService;

/**
 * Wraps the sending of iOS and Android Notifications to the Relay Service
 * Implements the encryption of the payload
 */
public abstract class PushNotificationService implements InstantNotificationService {

    private static final SecureRandom random = new SecureRandom();

    private static final Cipher cipher;

    static {
        try {
            cipher = Cipher.getInstance(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private static final Gson gson = new Gson();

    private final RestTemplate restTemplate;

    protected PushNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Send all the notifications requests to the endpoint. Potentially, optimize the sending using a batched request.
     *
     * @param requests           the requests previously built using buildSendRequest
     * @param relayServerBaseUrl the url of the relay
     */
    void sendNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        var futures = requests.stream()
                .map(request -> CompletableFuture.runAsync(() -> sendSpecificNotificationRequestsToEndpoint(Collections.singletonList(request), relayServerBaseUrl))).toList()
                .toArray(new CompletableFuture[0]);

        CompletableFuture.allOf(futures);
    }

    /**
     * Sends the actual request to the Hermes Relay Service (see here: https://github.com/ls1intum/Hermes)
     * It uses exponential backoff to retry once the request fails
     *
     * @param body               to be sent to Hermes. Differs between iOS and Android
     * @param relayServerBaseUrl the url where Hermes is hosted
     */
    @Async
    void sendRelayRequest(String body, String relayServerBaseUrl) {
        RetryTemplate template = RetryTemplate.builder().exponentialBackoff(1000, 4, 60 * 1000).retryOn(RestClientException.class).maxAttempts(4).build();

        try {
            template.execute((RetryCallback<Void, RestClientException>) context -> {
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);
                restTemplate.postForObject(relayServerBaseUrl + getRelayPath(), httpEntity, String.class);

                return null;
            });
        }
        catch (RestClientException e) {
            log.error("Could not send " + getDeviceType().toString() + " notifications");
        }
    }

    /**
     * Wrapper to handle a single user the same way as a list of users
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param user                who should be contacted
     * @param notificationSubject that is used to provide further information for mails (e.g. exercise, attachment, post, etc.)
     */
    @Override
    public final void sendNotification(Notification notification, User user, Object notificationSubject) {
        sendNotification(notification, Collections.singletonList(user), notificationSubject);
    }

    /**
     * Handles the finding of deviceConfigurations for all given users.
     * Constructs the payload to be sent and encrypts it with an AES256 encryption.
     * Sends the requests to the Hermes relay service with a fire and forget mechanism.
     *
     * @param notification        to be sent via the channel the implementing service is responsible for
     * @param users               who should be contacted
     * @param notificationSubject that is used to provide further information (e.g. exercise, attachment, post, etc.)
     */
    @Override
    @Async
    public void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
        final Optional<String> relayServerBaseUrl = getRelayBaseUrl();

        if (relayServerBaseUrl.isEmpty()) {
            return;
        }

        final NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());

        final List<PushNotificationDeviceConfiguration> userDeviceConfigurations = getRepository().findByUserIn(users, getDeviceType());
        if (userDeviceConfigurations.isEmpty()) {
            return;
        }

        final String date = Instant.now().toString();
        final String payload = gson.toJson(new PushNotificationData(notification.getTransientPlaceholderValuesAsArray(), notification.getTarget(), type.name(), date));

        final byte[] initializationVector = new byte[16];

        List<RelayNotificationRequest> notificationRequests = userDeviceConfigurations.stream().flatMap(deviceConfiguration -> {
            random.nextBytes(initializationVector);

            SecretKey key = new SecretKeySpec(deviceConfiguration.getSecretKey(), "AES");

            String ivAsString = Base64.getEncoder().encodeToString(initializationVector);
            Optional<String> payloadCiphertext = encrypt(payload, key, initializationVector);

            return payloadCiphertext.stream().map(s -> new RelayNotificationRequest(ivAsString, s, deviceConfiguration.getToken()));
        }).toList();

        sendNotificationRequestsToEndpoint(notificationRequests, relayServerBaseUrl.get());
    }

    protected abstract PushNotificationDeviceConfigurationRepository getRepository();

    abstract PushNotificationDeviceType getDeviceType();

    abstract Optional<String> getRelayBaseUrl();

    abstract String getRelayPath();

    abstract void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl);

    record PushNotificationData(String[] notificationPlaceholders, String target, String type, String date) {
    }

    /**
     * Perform symmetric AES encryption.
     *
     * @param payload              the text to encrypt
     * @param key                  the secret key to encrypt with
     * @param initializationVector the initialization vector needed for CBC
     * @return the ciphertext
     */
    private static Optional<String> encrypt(String payload, SecretKey key, byte[] initializationVector) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(initializationVector));

            return Optional.of(Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
        }
        catch (InvalidKeyException | InvalidAlgorithmParameterException | javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException e) {
            log.error("Error encrypting push notification payload!", e);
            return Optional.empty();
        }
    }
}
