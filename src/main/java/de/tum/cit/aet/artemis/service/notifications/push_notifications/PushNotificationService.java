package de.tum.cit.aet.artemis.service.notifications.push_notifications;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.constraints.NotNull;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.notification.Notification;
import de.tum.cit.aet.artemis.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.service.notifications.InstantNotificationService;

/**
 * Wraps the sending of iOS and Android Notifications to the Relay Service
 * Implements the encryption of the payload
 */
public abstract class PushNotificationService implements InstantNotificationService {

    private static final SecureRandom random = new SecureRandom();

    protected final ObjectMapper mapper = new ObjectMapper();

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
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures);
    }

    /**
     * Sends the actual request to the Hermes Relay Service (see here: <a href="https://github.com/ls1intum/Hermes">...</a>)
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
            log.error("Could not send {} notifications", getDeviceType().toString());
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
    @Async
    public void sendNotification(Notification notification, User user, Object notificationSubject) {
        sendNotification(notification, Set.of(user), notificationSubject);
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
    public void sendNotification(Notification notification, Set<User> users, Object notificationSubject) {
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
        var notificationData = new PushNotificationData(notification.getTransientPlaceholderValuesAsArray(), notification.getTarget(), type.name(), date,
                Constants.PUSH_NOTIFICATION_VERSION);

        try {
            final String payload = mapper.writeValueAsString(notificationData);
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
        catch (JsonProcessingException e) {
            log.error("Error creating push notification payload!", e);
        }
    }

    protected abstract PushNotificationDeviceConfigurationRepository getRepository();

    abstract PushNotificationDeviceType getDeviceType();

    abstract Optional<String> getRelayBaseUrl();

    abstract String getRelayPath();

    abstract void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl);

    record PushNotificationData(String[] notificationPlaceholders, String target, String type, String date, int version) {
    }

    /**
     * Perform symmetric AES encryption.
     *
     * @param payload              the text to encrypt
     * @param key                  the secret key to encrypt with
     * @param initializationVector the initialization vector needed for CBC
     * @return the ciphertext
     */
    private static Optional<String> encrypt(@NotNull String payload, SecretKey key, byte[] initializationVector) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(initializationVector));

            return Optional.of(Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
        }
        catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            log.error("Error encrypting push notification payload!", e);
            return Optional.empty();
        }
    }
}
