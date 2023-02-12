package de.tum.in.www1.artemis.service.notifications.push_notifications;

import java.security.*;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.service.notifications.InstantNotificationService;

public abstract class PushNotificationService<NOTIFICATION_REQUEST> implements InstantNotificationService {

    private static SecureRandom random = new SecureRandom();

    private static KeyFactory keyFactory;

    private static Cipher cipher;

    static {
        try {
            keyFactory = KeyFactory.getInstance(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);
            cipher = Cipher.getInstance(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private final PushNotificationDeviceConfigurationRepository repository;

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final Gson gson = new Gson();

    public PushNotificationService(PushNotificationDeviceConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * Build a send request using the given data that can be sent to the endpoint.
     *
     * @param initializationVector the iv needed to encrypt the payloadCiphertext on the client
     * @param payloadCiphertext    the encrypted payload
     * @param token                the endpoint specific token for the endpoint for one device
     * @return the send request
     */
    abstract NOTIFICATION_REQUEST buildSendRequest(String initializationVector, String payloadCiphertext, String token);

    /**
     * Send all the notifications requests to the endpoint. Potentially, optimize the sending using a batched request.
     *
     * @param requests the requests previously built using buildSendRequest
     */
    abstract void sendNotificationRequestsToEndpoint(List<NOTIFICATION_REQUEST> requests);

    @Override
    public final void sendNotification(Notification notification, User user, Object notificationSubject) {
        sendNotification(notification, Collections.singletonList(user), notificationSubject);
    }

    @Override
    public final void sendNotification(Notification notification, List<User> users, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());

        List<PushNotificationDeviceConfiguration> userDeviceConfigurations = repository.findByUserIn(users);
        if (!userDeviceConfigurations.isEmpty())
            return;

        String payload = gson.toJson(new PushNotificationData(notification.getTitle(), notification.getText(), notification.getTarget(), type.name()));

        final byte[] iv = new byte[16];

        List<NOTIFICATION_REQUEST> notificationRequests = userDeviceConfigurations.stream().flatMap(deviceConfiguration -> {
            random.nextBytes(iv);

            SecretKey key = new SecretKeySpec(deviceConfiguration.getSecretKey(), Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);

            String ivAsString = Base64.getEncoder().encodeToString(iv);
            Optional<String> payloadCiphertext = encrypt(payload, key, iv);

            if (payloadCiphertext.isPresent()) {
                return Stream.of(buildSendRequest(ivAsString, payloadCiphertext.get(), deviceConfiguration.getToken()));
            }
            else {
                return Stream.empty();
            }
        }).toList();

        sendNotificationRequestsToEndpoint(notificationRequests);
    }

    record PushNotificationData(String title, String body, String target, String type) {
    }

    /**
     * Perform symmetric AES encryption.
     *
     * @param payload the text to encrypt
     * @param key     the secret key to encrypt with
     * @param iv      the initialization vector needed for CBC
     * @return the ciphertext
     */
    private static Optional<String> encrypt(String payload, SecretKey key, byte[] iv) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

            return Optional.of(Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes())));
        }
        catch (InvalidKeyException | InvalidAlgorithmParameterException | javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException e) {
            log.error("Error encrypting push notification payload!", e);
            return Optional.empty();
        }
    }
}
